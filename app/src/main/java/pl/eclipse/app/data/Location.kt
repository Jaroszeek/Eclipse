package pl.eclipse.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Zgoda na przybliżone położenie — wystarcza, żeby wskazać najbliższy przystanek. */
const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

/**
 * Obecne położenie telefonu albo null, gdy nie ma zgody, dostawcy ani odczytu.
 * Współrzędne zostają w telefonie — służą tylko do ustawienia przystanków po odległości (CLAUDE.md).
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val last = PROVIDERS.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    // świeży odczyt wystarczy — przystanek obok nie ucieknie, a tak nie każemy czekać na nowy namiar
    if (last != null && System.currentTimeMillis() - last.time < FRESH_MILLIS) return last.latitude to last.longitude
    // dostawcy bywają włączeni, a mimo to nie odpowiadają, więc próbujemy po kolei i z ograniczeniem czasu
    val fresh = withTimeoutOrNull(TIMEOUT_MILLIS) {
        PROVIDERS.firstNotNullOfOrNull { provider ->
            if (runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                awaitLocation(manager, provider, context)
            } else {
                null
            }
        }
    }
    return (fresh ?: last)?.let { it.latitude to it.longitude }
}

@SuppressLint("MissingPermission")
private suspend fun awaitLocation(manager: LocationManager, provider: String, context: Context): Location? =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        runCatching {
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location -> continuation.resume(location) }
        }.onFailure { continuation.resume(null) }
    }

private val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
private const val FRESH_MILLIS = 5 * 60 * 1000L
private const val TIMEOUT_MILLIS = 15_000L
