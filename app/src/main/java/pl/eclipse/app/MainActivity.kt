package pl.eclipse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import pl.eclipse.app.ui.ReconScreen
import pl.eclipse.app.ui.diagnostics.DiagnosticsScreen
import pl.eclipse.app.ui.theme.EclipseTheme

// ponytail: do Etapu 2 aplikacja ma tylko ekran diagnostyczny i rekonesans; nawigacja powstanie z interfejsem.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EclipseTheme {
                var recon by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = recon) { recon = false }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (recon) ReconScreen(Modifier.padding(innerPadding))
                    else DiagnosticsScreen(onOpenRecon = { recon = true }, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
