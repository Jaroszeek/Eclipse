package pl.eclipse.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.notify.Notifier
import pl.eclipse.app.ui.EclipseRoot
import pl.eclipse.app.ui.RouteRequest
import pl.eclipse.app.ui.theme.EclipseTheme
import pl.eclipse.app.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    /** Żądanie otwarcia ekranu z powiadomienia; nowy obiekt przy każdym kliknięciu, nawet tej samej trasy. */
    private var route by mutableStateOf<RouteRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route = intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let(::RouteRequest)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = isDarkTheme(settings.themeMode)
            // Ikony pasków systemowych w kolorze pasującym do motywu aplikacji, nie systemu.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            EclipseTheme(settings.themeMode, androidx.compose.ui.graphics.Color(settings.accent), settings.lessTransparency) {
                EclipseRoot(route)
            }
        }
    }

    /** Aplikacja jest tylko po polsku — polskie zasady odmiany („2 dni”, „5 dni”) niezależnie od języka telefonu. */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("pl-PL")) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route = intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let(::RouteRequest)
    }
}
