package id.vern.antigravity.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.vern.antigravity.R
import id.vern.antigravity.api.GeminiClient
import id.vern.antigravity.ui.theme.AntigravityTheme

class ApiKeyScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var apiKey by remember { mutableStateOf("") }
        var status by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var navigateToMain by remember { mutableStateOf(false) }

        setContent {
            AntigravityTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.api_key_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.api_key_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(text = stringResource(R.string.api_key_label)) },
                            placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        status?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLoading) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                if (apiKey.isBlank()) {
                                    status = stringResource(R.string.api_key_empty_error)
                                    return@Button
                                }
                                isLoading = true
                                status = null
                                GeminiClient.saveApiKey(this@ApiKeyScreen, apiKey)
                                isLoading = false
                                status = stringResource(R.string.api_key_saved)
                                navigateToMain = true
                            },
                            enabled = apiKey.isNotBlank() && !isLoading
                        ) {
                            Text(stringResource(R.string.btn_save))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (GeminiClient.hasApiKey(this)) {
                            TextButton(
                                onClick = {
                                    navigateToMain = true
                                }
                            ) {
                                Text(stringResource(R.string.api_key_continue))
                            }
                        }
                    }
                }
            }
        }
    }
}
