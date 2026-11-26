package id.vern.antigravity.vision

import android.content.Context
import android.util.Log
import id.vern.antigravity.accessibility.ScreenControlService
import id.vern.antigravity.api.GeminiClient
import id.vern.antigravity.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ScreenAgent - the "look, decide, act, look again" loop.
 *
 * The model (Gemini 2.5 Flash) receives:
 *  - a screenshot of the display,
 *  - the accessibility tree with numbered tappable ids,
 *  - the user's goal,
 * and answers with function calls (tap / type / scroll / swipe / back / open_app / done).
 * Every action is executed through ScreenControlService and the loop repeats until the
 * model calls `task_finished` or the step budget runs out.
 */
object ScreenAgent {

    private const val TAG = "ScreenAgent"

    /** Hard stop so a confused model cannot tap forever. */
    const val MAX_STEPS = 25

    private val systemPrompt = """
        You are Antigravity, an on-device Android agent. You control a real phone the way a
        careful human would: look at the screen, decide the next action, perform it, then
        look again.

        Rules:
        1. Always act from the CURRENT observation. Never repeat an action that had no effect.
        2. Prefer tapping by node id (the [number] list) because it survives layout changes.
           Use raw coordinates only for things that are not in the tree (canvas, games, images).
        3. After typing, verify the text actually appeared before continuing.
        4. Before a destructive or money-related action (delete, purchase, sending a message),
           make sure the current screen really is the one the goal intends.
        5. If the goal is already achieved, call task_finished immediately.
        6. Keep replies short. The user only sees your summaries, not your reasoning.
        7. If you are stuck after 3 attempts at the same thing, call task_finished with
           success=false and explain what blocked you.
    """.trimIndent()

    // ------------------------------------------------------------------ state

    data class AgentState(
        val running: Boolean = false,
        val step: Int = 0,
        val lastAction: String = "",
        val status: String = "",
        val finished: Boolean = false,
        val success: Boolean = false,
        val summary: String = "",
        val error: String? = null,
        /** Base64 of the most recent screenshot, so the UI can show what the agent sees. */
        val lastScreenshotBase64: String? = null
    )

    private val mutableState = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = mutableState.asStateFlow()

    private var job: kotlinx.coroutines.Job? = null

    fun stop() {
        job?.cancel()
        job = null
        mutableState.value = mutableState.value.copy(running = false, status = "Stopped")
    }

    fun isRunning(): Boolean = mutableState.value.running

    /** Clears a finished run so the chat panel can be dismissed. */
    fun reset() {
        job = null
        mutableState.value = AgentState()
    }

    // ------------------------------------------------------------------- tools

    private fun toolDeclarations(): JSONArray {
        fun decl(
            name: String,
            description: String,
            props: Map<String, Pair<String, String>>,
            required: List<String>
        ): JSONObject {
            val properties = JSONObject()
            for ((key, typeDesc) in props) {
                properties.put(
                    key,
                    JSONObject().apply {
                        put("type", typeDesc.first)
                        put("description", typeDesc.second)
                    }
                )
            }
            return JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", properties)
                    put("required", JSONArray(required))
                })
            }
        }

        return JSONArray().apply {
            put(
                decl(
                    "tap",
                    "Tap the element with the given id from the current screen list.",
                    mapOf(
                        "id" to ("INTEGER" to "Node id shown in square brackets, e.g. 12")
                    ),
                    listOf("id")
                )
            )
            put(
                decl(
                    "tap_xy",
                    "Tap absolute screen coordinates. Only for elements missing from the tree.",
                    mapOf(
                        "x" to ("INTEGER" to "Absolute x coordinate in pixels"),
                        "y" to ("INTEGER" to "Absolute y coordinate in pixels")
                    ),
                    listOf("x", "y")
                )
            )
            put(
                decl(
                    "type_text",
                    "Type text into an input field. Provide the field id when known.",
                    mapOf(
                        "id" to ("INTEGER" to "Id of the input field (optional)"),
                        "text" to ("STRING" to "Text to type")
                    ),
                    listOf("text")
                )
            )
            put(
                decl(
                    "scroll",
                    "Scroll the screen to reveal more content.",
                    mapOf("direction" to ("STRING" to "One of: up, down, left, right")),
                    listOf("direction")
                )
            )
            put(
                decl(
                    "swipe",
                    "Swipe between two absolute points, e.g. to dismiss a card or unlock.",
                    mapOf(
                        "from_x" to ("INTEGER" to "Start x"),
                        "from_y" to ("INTEGER" to "Start y"),
                        "to_x" to ("INTEGER" to "End x"),
                        "to_y" to ("INTEGER" to "End y")
                    ),
                    listOf("from_x", "from_y", "to_x", "to_y")
                )
            )
            put(decl("back", "Press the system back button.", emptyMap(), emptyList()))
            put(decl("home", "Go to the home screen.", emptyMap(), emptyList()))
            put(
                decl(
                    "open_app",
                    "Launch an installed app by package name, e.g. com.android.settings.",
                    mapOf("package" to ("STRING" to "Android package name")),
                    listOf("package")
                )
            )
            put(decl("wait", "Wait a moment for the UI to settle.", emptyMap(), emptyList()))
            put(
                decl(
                    "task_finished",
                    "Call this when the goal is achieved, or when you are blocked.",
                    mapOf(
                        "success" to ("BOOLEAN" to "true when the goal was achieved"),
                        "summary" to ("STRING" to "One or two sentences for the user")
                    ),
                    listOf("success", "summary")
                )
            )
        }
    }

    // -------------------------------------------------------------------- loop

    /**
     * Starts the agent for [goal]. Returns immediately; observe [state] for progress.
     */
    fun start(
        context: Context,
        scope: CoroutineScope,
        goal: String,
        screenWidth: Int,
        screenHeight: Int,
        onLog: (String) -> Unit = {}
    ) {
        if (job?.isActive == true) {
            onLog("Agent is already running.")
            return
        }

        val service = ScreenControlService.getInstance()
        if (service == null) {
            mutableState.value = AgentState(
                error = "Accessibility service is off. Enable it in Settings > Accessibility, then retry.",
                finished = true
            )
            return
        }
        if (!GeminiClient.hasApiKey(context)) {
            mutableState.value = AgentState(
                error = "Add your Gemini API key first.",
                finished = true
            )
            return
        }

        job = scope.launch(Dispatchers.IO) {
            mutableState.value = AgentState(running = true, status = "Reading the screen...")
            val history = JSONArray()
            var step = 0
            var finished = false

            try {
                while (step < MAX_STEPS && !finished) {
                    step++

                    val screenText = withContext(Dispatchers.Main) { service.describeScreen() }
                    val shot = ScreenCaptureManager.captureBase64Jpeg()
                    mutableState.value = mutableState.value.copy(
                        step = step,
                        status = "Thinking (step $step)...",
                        lastScreenshotBase64 = shot ?: mutableState.value.lastScreenshotBase64
                    )
                    onLog("step $step: observing screen")

                    history.put(
                        GeminiClient.buildObservationParts(goal, screenText, shot, step)
                    )

                    val turn = GeminiClient.screenAgentTurn(
                        context = context,
                        history = history,
                        screenshotJpegBase64 = null, // already embedded in the observation part
                        screenText = screenText,
                        userGoal = goal,
                        systemInstruction = systemPrompt,
                        tools = toolDeclarations()
                    )

                    if (turn.error != null) {
                        mutableState.value = mutableState.value.copy(
                            running = false,
                            finished = true,
                            error = turn.error,
                            status = "Stopped: ${turn.error}"
                        )
                        return@launch
                    }

                    turn.modelContent?.let { history.put(it) }

                    if (turn.calls.isEmpty()) {
                        // Model answered with text only: treat a plain answer as completion.
                        val summary = turn.text ?: "No action requested."
                        mutableState.value = mutableState.value.copy(
                            running = false,
                            finished = true,
                            success = true,
                            summary = summary,
                            status = "Finished"
                        )
                        return@launch
                    }

                    val results = mutableListOf<Pair<String, JSONObject>>()
                    for (call in turn.calls) {
                        val outcome = withContext(Dispatchers.Main) {
                            execute(service, call, screenWidth, screenHeight)
                        }
                        mutableState.value = mutableState.value.copy(lastAction = call.name)
                        onLog("action: ${call.name} -> $outcome")
                        results.add(call.name to outcome.response)

                        if (call.name == "task_finished") {
                            val ok = call.args.optBoolean("success", true)
                            val summary = call.args.optString("summary", "Done.")
                            mutableState.value = mutableState.value.copy(
                                running = false,
                                finished = true,
                                success = ok,
                                summary = summary,
                                status = if (ok) "Finished" else "Stopped"
                            )
                            finished = true
                            break
                        }
                    }

                    if (!finished) {
                        history.put(GeminiClient.buildFunctionResponseTurn(results))
                    }
                }

                if (!finished) {
                    mutableState.value = mutableState.value.copy(
                        running = false,
                        finished = true,
                        success = false,
                        error = "Step limit ($MAX_STEPS) reached before the goal was completed.",
                        summary = "I stopped after $MAX_STEPS steps without finishing.",
                        status = "Stopped"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                mutableState.value = mutableState.value.copy(running = false, status = "Stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Agent loop failed", e)
                mutableState.value = mutableState.value.copy(
                    running = false,
                    finished = true,
                    error = e.message ?: "Agent failed",
                    status = "Failed"
                )
            }
        }
    }

    private data class Outcome(val response: JSONObject)

    private fun execute(
        service: ScreenControlService,
        call: GeminiClient.ToolCall,
        screenWidth: Int,
        screenHeight: Int
    ): Outcome {
        val args = call.args
        return try {
            when (call.name) {
                "tap" -> {
                    val id = args.optInt("id", -1)
                    val ok = service.clickNode(id)
                    ok(ok)
                }
                "tap_xy" -> ok(
                    service.tap(args.optDouble("x", 0.0).toFloat(), args.optDouble("y", 0.0).toFloat())
                )
                "type_text" -> {
                    val id = if (args.has("id")) args.optInt("id", -1) else null
                    ok(service.typeInto(id?.takeIf { it > 0 }, args.optString("text")))
                }
                "scroll" -> ok(service.scroll(args.optString("direction", "down"), screenWidth, screenHeight))
                "swipe" -> ok(
                    service.swipe(
                        args.optDouble("from_x", 0.0).toFloat(),
                        args.optDouble("from_y", 0.0).toFloat(),
                        args.optDouble("to_x", 0.0).toFloat(),
                        args.optDouble("to_y", 0.0).toFloat()
                    )
                )
                "back" -> ok(service.pressBack())
                "home" -> ok(service.pressHome())
                "open_app" -> ok(service.openApp(args.optString("package")))
                "wait" -> {
                    Thread.sleep(1200)
                    ok(true)
                }
                "task_finished" -> ok(true)
                else -> Outcome(JSONObject().put("ok", false).put("error", "Unknown action ${call.name}"))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Action ${call.name} failed: ${e.message}")
            Outcome(JSONObject().put("ok", false).put("error", e.message ?: "action failed"))
        }
    }

    private fun ok(success: Boolean): Outcome =
        Outcome(JSONObject().put("ok", success))
}
