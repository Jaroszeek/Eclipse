package pl.eclipse.app.ui.diagnostics

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.eclipse.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Prosty ekran diagnostyczny (Etap 1b) — sprawdzenie danych i synchronizacji na telefonie przed budową interfejsu.

private val TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Warsaw"))

@Composable
fun DiagnosticsScreen(onOpenRecon: () -> Unit, modifier: Modifier = Modifier, viewModel: DiagnosticsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineSmall)
        if (state.demo) Text(stringResource(R.string.demo_badge), color = MaterialTheme.colorScheme.primary)
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

        if (!state.hasCredentials && !state.demo) {
            LoginForm(loggingIn = state.loggingIn, onLogin = viewModel::login)
            TextButton(onClick = { viewModel.useDemo(true) }) { Text(stringResource(R.string.try_demo)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.demo_mode), Modifier.weight(1f))
                Switch(checked = state.demo, onCheckedChange = viewModel::useDemo, enabled = state.hasCredentials || state.demo)
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.diag_counts), style = MaterialTheme.typography.titleMedium)
        if (state.counts.isEmpty()) Text(stringResource(R.string.diag_no_data))
        state.counts.toSortedMap().forEach { (type, count) -> Text("$type: $count") }

        HorizontalDivider()
        Text(
            stringResource(if (state.periodicScheduled) R.string.diag_periodic_on else R.string.diag_periodic_off),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = viewModel::syncNow, enabled = !state.syncRunning && (state.demo || state.hasCredentials), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.syncRunning) R.string.sync_running else R.string.sync_now))
        }
        OutlinedButton(onClick = viewModel::sendTestNotification, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.send_test_notification))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.allow_notifications))
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.diag_runs), style = MaterialTheme.typography.titleMedium)
        if (state.runs.isEmpty()) Text(stringResource(R.string.diag_no_runs))
        state.runs.forEach { run ->
            val status = stringResource(if (run.success) R.string.diag_run_ok else R.string.diag_run_error)
            Text("${TIME.format(Instant.ofEpochMilli(run.startedAt))} — $status", style = MaterialTheme.typography.labelLarge)
            if (run.summary.isNotBlank()) Text(run.summary, style = MaterialTheme.typography.bodySmall)
            if (run.errors.isNotBlank()) Text(run.errors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider()
        TextButton(onClick = onOpenRecon) { Text(stringResource(R.string.open_recon)) }
        if (state.hasCredentials || state.demo) {
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) }
        }
    }
}

@Composable
private fun LoginForm(loggingIn: Boolean, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    Text(stringResource(R.string.login_info), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
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
        onClick = { onLogin(email, password) },
        enabled = !loggingIn && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (loggingIn) R.string.logging_in else R.string.log_in))
    }
}
