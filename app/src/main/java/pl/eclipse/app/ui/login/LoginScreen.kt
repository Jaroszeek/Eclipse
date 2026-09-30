package pl.eclipse.app.ui.login

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.eclipse.app.CrashLog
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.LoginError
import pl.eclipse.app.ui.components.EclipseBackground
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EclipseDisc
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.theme.Eclipse

/** Pierwsze uruchomienie (SPEC 12.9). Hasło trzymane w `remember`, nie w `rememberSaveable` — nie trafia do zapisanego stanu. */
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val account = context.container.account
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LoginError?>(null) }
    val c = Eclipse.colors

    fun submit() {
        if (busy || email.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            error = account.login(email, password)
            if (error == null) password = ""
            busy = false
        }
    }

    EclipseBackground {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EclipseDisc(coverage = 0f, size = 180.dp, showProgress = false, animateIn = true)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = c.text)
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.login_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painterResource(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { e ->
                    val message = stringResource(e.message)
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { error(message) })
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                PrimaryButton(stringResource(if (busy) R.string.logging_in else R.string.log_in), ::submit, Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.isNotEmpty())
                TextButton(onClick = { scope.launch { account.useDemo(true) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.try_demo))
                }
                Text(
                    stringResource(R.string.login_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val LoginError.message
    get() = when (this) {
        LoginError.CREDENTIALS -> R.string.login_error_credentials
        LoginError.NETWORK -> R.string.login_error_network
        LoginError.SERVER -> R.string.login_error_server
        LoginError.CAPTCHA -> R.string.login_error_captcha
        LoginError.MAINTENANCE -> R.string.login_error_maintenance
        LoginError.OTHER -> R.string.login_error_other
    }

/**
 * Po zalogowaniu: pierwsza synchronizacja z paskiem postępu (SPEC 12.9). Gdy się nie uda albo trwa za długo,
 * ekran pokazuje przyczynę i wyjście: ponowną próbę albo wylogowanie.
 */
@Composable
fun FirstSyncScreen(
    running: Boolean,
    waiting: Boolean,
    error: String?,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    // Zleca pobieranie od nowa, np. po wymuszonym zatrzymaniu aplikacji; gdy zadanie już czeka albo trwa, nic nie zmienia.
    LaunchedEffect(Unit) { onStart() }
    val busy = running || waiting
    val failed = !busy && error != null
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        slow = false
        delay(SLOW_AFTER_MS)
        slow = true
    }
    val crash = remember { CrashLog.read(context) }
    val c = Eclipse.colors
    EclipseBackground {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EclipseDisc(coverage = 0.5f, size = 160.dp, showProgress = false)
                Spacer(Modifier.height(24.dp))
                val title = when {
                    failed -> R.string.first_sync_failed
                    waiting && !running -> R.string.first_sync_waiting
                    else -> R.string.first_sync
                }
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                if (failed) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().widthIn(max = 360.dp))
                    if (slow) {
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.first_sync_slow), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
                    }
                }
                if (failed || slow) {
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(stringResource(R.string.first_sync_retry), onRetry, Modifier.fillMaxWidth().widthIn(max = 360.dp))
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(stringResource(R.string.first_sync_logout), onLogout, Modifier.fillMaxWidth().widthIn(max = 360.dp))
                    crash?.let {
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.crash_last), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

private const val SLOW_AFTER_MS = 30_000L

/** Krótki ekran z wyjaśnieniem przed prośbą o zgodę na powiadomienia (SPEC 9, Android 13+). */
@Composable
fun NotificationPermissionScreen(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val c = Eclipse.colors
    EclipseBackground {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EclipseCard(Modifier.widthIn(max = 480.dp)) {
                Icon(painterResource(R.drawable.ic_notifications), null, tint = c.accentText)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall, color = c.text)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.permission_allow), {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone()
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.permission_later), onDone, Modifier.fillMaxWidth())
            }
        }
    }
}
