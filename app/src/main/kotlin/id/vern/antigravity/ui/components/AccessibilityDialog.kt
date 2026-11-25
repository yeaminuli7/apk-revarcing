package id.vern.antigravity.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.vern.antigravity.R
import id.vern.antigravity.accessibility.AccessibilityHelper
import id.vern.antigravity.accessibility.ScreenControlService

@Composable
fun AccessibilityDialog(
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val isEnabled = remember {
        AccessibilityHelper.isServiceEnabled(context)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.accessibility_permission_title))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onOpenSettings()
                    onDismissRequest()
                },
                enabled = true
            ) {
                Text(stringResource(R.string.accessibility_btn_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = { onSkip() }) {
                Text(stringResource(R.string.accessibility_btn_skip))
            }
        },
        text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = if (isEnabled) {
                    stringResource(R.string.accessibility_service_on)
                } else {
                    stringResource(R.string.accessibility_service_off)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "স্ক্রিন কন্ট্রোলের জন্য অ্যাক্সেসিবিলিটি সার্ভিস অন করতে হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
    )
}
