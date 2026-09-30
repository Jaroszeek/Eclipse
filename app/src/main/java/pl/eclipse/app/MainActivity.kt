package pl.eclipse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import pl.eclipse.app.ui.ReconScreen
import pl.eclipse.app.ui.theme.EclipseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EclipseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReconScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}
