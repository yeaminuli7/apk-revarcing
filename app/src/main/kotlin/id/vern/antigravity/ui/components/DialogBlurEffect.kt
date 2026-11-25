package id.vern.antigravity.ui.components

import android.app.Dialog
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

object DialogBlurState {
    val activeDialogCount = mutableIntStateOf(0)
    val isBlurActive: Boolean
        get() = activeDialogCount.intValue > 0
}

@Composable
fun DialogBlurEffect(radiusDp: Int = 24) {
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.dp.roundToPx() }

    DisposableEffect(Unit) {
        DialogBlurState.activeDialogCount.intValue++
        onDispose {
            DialogBlurState.activeDialogCount.intValue = maxOf(0, DialogBlurState.activeDialogCount.intValue - 1)
        }
    }

    DisposableEffect(view) {
        val window = findDialogWindow(view)
        if (window != null) {
            window.setDimAmount(0.32f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val params = window.attributes
                params.blurBehindRadius = radiusPx
                window.attributes = params
            }
        }
        onDispose {}
    }
}

private fun findDialogWindow(view: View): Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Dialog) {
            return context.window
        }
        context = context.baseContext
    }
    return null
}

