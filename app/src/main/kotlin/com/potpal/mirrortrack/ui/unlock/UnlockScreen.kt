package com.potpal.mirrortrack.ui.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFirstRun = viewModel.isFirstRun

    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Zero passphrase state when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            passphrase = ""
            confirmPassphrase = ""
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is UnlockUiState.Success -> onUnlocked()
            is UnlockUiState.Error -> {
                errorMessage = (state as UnlockUiState.Error).message
                passphrase = ""
                confirmPassphrase = ""
            }
            is UnlockUiState.PanicWiped -> {
                passphrase = ""
                confirmPassphrase = ""
                viewModel.resetState()
            }
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "MirrorTrack",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isFirstRun) "Create a passphrase to encrypt your data"
                   else "Enter your passphrase to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                errorMessage = null
            },
            label = { Text("Passphrase") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isFirstRun) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (!isFirstRun && passphrase.isNotEmpty()) {
                        doUnlock(passphrase, viewModel)
                        passphrase = ""
                    }
                }
            ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff
                                      else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide" else "Show"
                    )
                }
            },
            isError = errorMessage != null
        )

        if (isFirstRun) {
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassphrase,
                onValueChange = {
                    confirmPassphrase = it
                    errorMessage = null
                },
                label = { Text("Confirm passphrase") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (passphrase.isNotEmpty() && passphrase == confirmPassphrase) {
                            doUnlock(passphrase, viewModel)
                            passphrase = ""
                            confirmPassphrase = ""
                        }
                    }
                ),
                isError = errorMessage != null
            )
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (isFirstRun) {
                    if (passphrase.isEmpty()) {
                        errorMessage = "Passphrase cannot be empty"
                    } else if (passphrase != confirmPassphrase) {
                        errorMessage = "Passphrases do not match"
                    } else {
                        errorMessage = null
                        doUnlock(passphrase, viewModel)
                        passphrase = ""
                        confirmPassphrase = ""
                    }
                } else {
                    if (passphrase.isEmpty()) {
                        errorMessage = "Passphrase cannot be empty"
                    } else {
                        errorMessage = null
                        doUnlock(passphrase, viewModel)
                        passphrase = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is UnlockUiState.Loading
        ) {
            if (state is UnlockUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (isFirstRun) "Create & Unlock" else "Unlock")
            }
        }

        Spacer(Modifier.weight(2f))
    }
}

private fun doUnlock(passphrase: String, viewModel: UnlockViewModel) {
    val chars = passphrase.toCharArray()
    viewModel.unlock(chars)
}
