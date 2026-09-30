package pl.eclipse.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.core.recon.Recon

// ponytail: tymczasowy ekran bez ViewModelu, tylko na czas rekonesansu (Etap 1a);
// zastąpi go ekran diagnostyczny z Etapu 1b. Hasło trzymane w `remember`, nie w `rememberSaveable`,
// żeby nie trafiło do zapisanego stanu aktywności.
@Composable
fun ReconScreen(modifier: Modifier = Modifier) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.recon_copied)
    val noLogin = stringResource(R.string.recon_messages_no_login)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.recon_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.recon_info), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text(stringResource(R.string.login_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password_label)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(stringResource(if (showPassword) R.string.hide_password else R.string.show_password))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                running = true
                report = null
                val credentials = login.trim() to password
                password = ""
                scope.launch {
                    report = withContext(Dispatchers.IO) {
                        runCatching { Recon.run(credentials.first, credentials.second) }
                            .getOrElse { "Rekonesans przerwany: ${it.javaClass.simpleName}" }
                    }
                    running = false
                }
            },
            enabled = !running && login.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (running) R.string.recon_running else R.string.recon_start))
        }
        // Wiadomości: z logowaniem zapisanym w aplikacji — hasła nie trzeba wpisywać drugi raz.
        OutlinedButton(
            onClick = {
                running = true
                report = null
                scope.launch {
                    val saved = context.container.credentials.read()
                    report = if (saved == null) noLogin else withContext(Dispatchers.IO) {
                        runCatching { Recon.messages(saved.email, saved.password) }
                            .getOrElse { "Rekonesans przerwany: ${it.javaClass.simpleName}" }
                    }
                    running = false
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.recon_messages))
        }
        report?.let { text ->
            OutlinedButton(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Raport", text))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recon_copy))
            }
            SelectionContainer {
                Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
