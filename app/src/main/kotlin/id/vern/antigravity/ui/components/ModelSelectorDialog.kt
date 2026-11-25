package id.vern.antigravity.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.vern.antigravity.R
import id.vern.antigravity.data.CodeAssistTier
import id.vern.antigravity.data.SubscriptionTierType
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.UserQuotaSummary
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.CliModel
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen

val ThinkingEfforts = listOf("Low", "Medium", "High")

data class DisplayAiModel(
    val id: String,
    val name: String,
    val provider: String,
    val description: String,
    val iconRes: Int? = null,
    val iconVector: ImageVector? = null,

    val cliId: String? = null,

    val effortCliIds: Map<String, String> = emptyMap(),

    val efforts: List<String> = ThinkingEfforts,

    val descriptionRes: Int? = null
) {

    fun cliIdFor(effort: String?): String? {
        if (effort != null) {
            effortCliIds.entries.firstOrNull { it.key.equals(effort, ignoreCase = true) }?.let { return it.value }
        }
        return cliId
            ?: effortCliIds.entries.firstOrNull { it.key.equals("Medium", ignoreCase = true) }?.value
            ?: effortCliIds.values.firstOrNull()
    }
}

fun List<CliModel>.toDisplayAiModels(): List<DisplayAiModel> {
    val grouped = linkedMapOf<String, MutableList<CliModel>>()
    forEach { grouped.getOrPut(it.displayName) { mutableListOf() }.add(it) }
    return grouped.values.map { variants ->
        val first = variants.first()
        val bare = variants.firstOrNull { it.effort.isEmpty() }
        val effortCliIds = variants.filter { it.effort.isNotEmpty() }
            .associate { it.effort to it.cliId }
        val provider = first.provider.ifBlank { AgyModelsService.providerForDisplayName(first.displayName) }
        DisplayAiModel(
            id = first.displayName,
            name = first.displayName,
            provider = provider,
            description = "",
            descriptionRes = null,
            iconRes = when {
                provider.equals("Google", ignoreCase = true) || first.displayName.contains("Gemini", ignoreCase = true) -> R.drawable.ic_model_gemini
                provider.equals("Anthropic", ignoreCase = true) || first.displayName.contains("Claude", ignoreCase = true) -> R.drawable.ic_model_claude
                provider.equals("OpenAI", ignoreCase = true) || first.displayName.contains("GPT", ignoreCase = true) -> R.drawable.ic_model_gpt
                else -> null
            },
            cliId = bare?.cliId,
            effortCliIds = effortCliIds,
            efforts = AgyModelsService.EFFORT_LEVELS.filter { it in effortCliIds }
        )
    }
}

internal fun getModelQuotaPercentage(
    modelId: String,
    summary: UserQuotaSummary?
): Pair<Int?, String?> {
    if (summary == null) return Pair(null, null)
    val geminiGroup = summary.groups.firstOrNull { it.displayName.contains("gemini", ignoreCase = true) && !it.displayName.contains("argon", ignoreCase = true) }
    val argonGroup = summary.groups.firstOrNull { it.displayName.contains("argon", ignoreCase = true) }
    val thirdPartyGroup = summary.groups.firstOrNull {
        it.displayName.contains("claude", ignoreCase = true) || it.displayName.contains("gpt", ignoreCase = true)
    }

    val isFlashOrNeon = modelId.contains("Flash", ignoreCase = true) || modelId.contains("Neon", ignoreCase = true)
    if (isFlashOrNeon) {
        val fiveHB = geminiGroup?.buckets?.firstOrNull { it.window.equals("5h", ignoreCase = true) || it.bucketId.contains("5h", ignoreCase = true) }
        val weeklyB = geminiGroup?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }
        if (fiveHB != null && weeklyB != null) {
            val sub = "${fiveHB.subtext ?: ""} (Weekly: ${weeklyB.percentage}%)".trim()
            return Pair(fiveHB.percentage, sub)
        }
    }

    val matchingAvail = summary.availableModels.firstOrNull { it.matches(modelId) }
    if (matchingAvail != null && matchingAvail.percentage != null) {
        val sub = matchingAvail.resetTime?.let { id.vern.antigravity.service.CloudCodeQuotaService.formatSubtext(it) }
        return Pair(matchingAvail.percentage, sub)
    }

    return when {
        modelId.contains("Argon", ignoreCase = true) -> {
            val weeklyB = argonGroup?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }
                ?: argonGroup?.buckets?.firstOrNull()
                ?: geminiGroup?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }
            val sub = weeklyB?.subtext ?: matchingAvail?.resetTime?.let { id.vern.antigravity.service.CloudCodeQuotaService.formatSubtext(it) }
            val pct = weeklyB?.percentage ?: if (matchingAvail?.resetTime != null) 0 else null
            Pair(pct, sub)
        }
        isFlashOrNeon -> {
            val fiveHB = geminiGroup?.buckets?.firstOrNull { it.window.equals("5h", ignoreCase = true) || it.bucketId.contains("5h", ignoreCase = true) }
            val weeklyB = geminiGroup?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }
            val b = fiveHB ?: weeklyB
            Pair(b?.percentage, b?.subtext)
        }
        modelId.contains("Hydrogen", ignoreCase = true) || modelId.contains("Carbon", ignoreCase = true) || modelId.contains("Gemini", ignoreCase = true) -> {
            val weeklyB = geminiGroup?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }
                ?: geminiGroup?.buckets?.firstOrNull()
            val sub = weeklyB?.subtext ?: matchingAvail?.resetTime?.let { id.vern.antigravity.service.CloudCodeQuotaService.formatSubtext(it) }
            val pct = weeklyB?.percentage ?: if (matchingAvail?.resetTime != null) 0 else null
            Pair(pct, sub)
        }
        modelId.contains("Claude", ignoreCase = true) || modelId.contains("Sonnet", ignoreCase = true) || modelId.contains("Opus", ignoreCase = true) || modelId.contains("GPT", ignoreCase = true) -> {
            val b = thirdPartyGroup?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }
                ?: thirdPartyGroup?.buckets?.firstOrNull()
            Pair(b?.percentage, b?.subtext)
        }
        else -> Pair(null, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorDialog(
    selectedModel: String,
    selectedThinking: String,
    userQuotaSummary: UserQuotaSummary? = null,
    codeAssistTier: CodeAssistTier? = null,
    cliModels: List<CliModel> = emptyList(),
    modelsErrorMessage: String? = null,
    onModelSelected: (model: String, thinking: String, cliId: String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var showUsageDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val displayedModels = remember(cliModels) {
        cliModels.toDisplayAiModels()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.dialog_model_selector_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val tierType = codeAssistTier?.tierType ?: SubscriptionTierType.FREE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (tierType) {
                            SubscriptionTierType.ULTRA -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            SubscriptionTierType.PRO -> AntigravityBlue.copy(alpha = 0.15f)
                            SubscriptionTierType.ENTERPRISE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when (tierType) {
                                SubscriptionTierType.ULTRA -> stringResource(R.string.account_tier_ultra)
                                SubscriptionTierType.PRO -> stringResource(R.string.account_tier_pro)
                                SubscriptionTierType.ENTERPRISE -> stringResource(R.string.account_tier_enterprise)
                                else -> stringResource(R.string.account_tier_free)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (tierType) {
                                SubscriptionTierType.ULTRA -> MaterialTheme.colorScheme.tertiary
                                SubscriptionTierType.PRO -> AntigravityBlue
                                SubscriptionTierType.ENTERPRISE -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayedModels.forEach { model ->
                        val isSelected = model.id.equals(selectedModel, ignoreCase = true) ||
                                model.name.equals(selectedModel, ignoreCase = true) ||
                                model.cliId == selectedModel ||
                                model.effortCliIds.values.contains(selectedModel)
                        val quotaModel = remember(model.id, userQuotaSummary) {
                            userQuotaSummary?.availableModels?.firstOrNull { it.matches(model.id) }
                        }
                        val (quotaPct, quotaSubtext) = remember(model.id, userQuotaSummary) {
                            getModelQuotaPercentage(model.id, userQuotaSummary)
                        }

                        val modelAvailability = quotaModel?.availability ?: ModelAvailability.UNKNOWN
                        val isAvailableForAccount = modelAvailability != ModelAvailability.UNAVAILABLE

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                else if (!isAvailableForAccount) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAvailableForAccount) 0.3f else 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isAvailableForAccount) {
                                        onModelSelected(model.id, selectedThinking, model.cliIdFor(selectedThinking))
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.model_toast_not_available, model.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .alpha(if (isAvailableForAccount) 1f else 0.55f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (model.iconRes != null) {
                                        Icon(
                                            painter = painterResource(model.iconRes),
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (model.iconVector != null) {
                                        Icon(
                                            imageVector = model.iconVector,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!isAvailableForAccount) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(10.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                val lockLabel = stringResource(R.string.model_status_unavailable_account)
                                                Text(
                                                    text = lockLabel,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                val descText = model.descriptionRes?.let { stringResource(it, model.provider) } ?: model.description
                                if (descText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = descText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }

                                if (quotaPct != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "$quotaPct% remaining",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (quotaPct > 20) AntigravityGreen else MaterialTheme.colorScheme.error
                                        )
                                        if (!quotaSubtext.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "· $quotaSubtext",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (model.effortCliIds.size >= 2 && isSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Reasoning:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        model.efforts.forEach { effort ->
                                            val isEffortSelected = selectedThinking.equals(effort, ignoreCase = true)
                                            FilterChip(
                                                selected = isEffortSelected,
                                                onClick = {

                                                    onModelSelected(model.id, effort, model.cliIdFor(effort))
                                                },
                                                label = {
                                                    Text(
                                                        text = effort,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isEffortSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isEffortSelected,
                                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                    selectedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!modelsErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = modelsErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                val allQuotaBuckets = remember(userQuotaSummary) {
                    userQuotaSummary?.groups?.flatMap { it.buckets } ?: emptyList()
                }
                val hasZeroQuota = remember(allQuotaBuckets) {
                    allQuotaBuckets.any { it.percentage == 0 }
                }
                val lowestQuotaPct = remember(allQuotaBuckets) {
                    allQuotaBuckets.minOfOrNull { it.percentage }
                }
                val usageIconColor = when {
                    hasZeroQuota -> Color(0xFFEF5350)
                    lowestQuotaPct != null && lowestQuotaPct < 50 -> Color(0xFFFFB300)
                    else -> AntigravityGreen
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { showUsageDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = usageIconColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.model_btn_view_usage),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AntigravityBlue
                        )
                    }
                }
            }
        }

    if (showUsageDialog) {
        UsageLimitsDialog(
            userQuotaSummary = userQuotaSummary,
            codeAssistTier = codeAssistTier,
            onDismiss = { showUsageDialog = false }
        )
    }
}

@Composable
fun UsageLimitsDialog(
    userQuotaSummary: UserQuotaSummary?,
    codeAssistTier: CodeAssistTier? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val groups = userQuotaSummary?.groups ?: emptyList()
    val curTier = codeAssistTier?.currentTier

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogBlurEffect()
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier
                .widthIn(min = 320.dp, max = 400.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.usage_limits_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (curTier != null && curTier.name.isNotBlank()) {
                        val planName = curTier.name
                        val upgradeDesc = curTier.upgradeSubscriptionText.ifBlank { curTier.description }
                        val upgradeUri = curTier.upgradeSubscriptionUri.ifBlank {
                            codeAssistTier?.paidTier?.upgradeSubscriptionUri ?: ""
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = "Your Plan: $planName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (upgradeDesc.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = upgradeDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                if (upgradeUri.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(upgradeUri))
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.common_btn_upgrade),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (groups.isEmpty()) {
                        Text(
                            text = stringResource(R.string.usage_limits_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        groups.forEach { group ->
                            QuotaGroupCard(group = group)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaGroupCard(
    group: id.vern.antigravity.data.QuotaGroup
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = group.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                group.buckets.forEachIndexed { bucketIndex, bucket ->
                    if (bucketIndex > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    QuotaLimitItemRow(
                        label = bucket.displayName,
                        subtext = bucket.description.ifBlank { bucket.subtext ?: "" },
                        percentage = bucket.percentage,
                        disabled = bucket.disabled
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaLimitItemRow(
    label: String,
    subtext: String,
    percentage: Int,
    disabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (subtext.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp)
            ) {
                val sweepAngle = (percentage / 100f) * 360f
                val strokeColor = when {
                    disabled -> Color(0xFF3F3F46)
                    percentage == 0 -> Color(0xFFEF5350)
                    percentage < 50 -> Color(0xFFFFB300)
                    else -> AntigravityGreen
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    drawArc(
                        color = Color(0xFF27272A),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = strokeColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

