package pl.eclipse.core.transit

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// SPEC 17.1. Jedyne miejsce w aplikacji, które łączy się poza Librusa — i tylko po to, żeby POBRAĆ
// publiczny rozkład. Nie wysyłamy tu żadnych danych o użytkowniku (CLAUDE.md).

/** Rozkłady ZTP Kraków w formacie GTFS. */
enum class TransitFeed(private val fileName: String) {
    TRAM("GTFS_KRK_T.zip"),
    BUS("GTFS_KRK_A.zip");

    val url: String get() = "https://gtfs.ztp.krakow.pl/$fileName"
}

/**
 * Pobiera rozkład do pliku [target]. [onProgress] dostaje liczbę pobranych bajtów i rozmiar całości
 * (albo -1, gdy serwer go nie podaje). Plik podmieniamy dopiero po udanym pobraniu.
 */
fun downloadFeed(feed: TransitFeed, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
    val partial = File(target.parentFile, target.name + ".part")
    http.newCall(Request.Builder().url(feed.url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Serwer ZTP odpowiedział błędem ${response.code}")
        val total = response.body.contentLength()
        response.body.byteStream().use { input ->
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    onProgress(done, total)
                }
            }
        }
    }
    target.delete()
    if (!partial.renameTo(target)) throw IOException("Nie udało się zapisać pobranego rozkładu")
}

private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
