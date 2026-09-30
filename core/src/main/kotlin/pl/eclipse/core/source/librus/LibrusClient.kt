package pl.eclipse.core.source.librus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class LibrusException(message: String) : Exception(message)

/** Sesja z Librusem: logowanie przez OAuth portalu, potem zapytania z ciasteczkami sesji. */
class LibrusClient {
    private val cookieJar = MemoryCookieJar()

    /** Przebieg zapytań do diagnostyki: metoda, host, ścieżka (bez parametrów) i kod odpowiedzi. */
    val trace = mutableListOf<String>()

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request).also { response ->
                trace += "${request.method} ${request.url.host}${request.url.encodedPath} → ${response.code}"
            }
        }
        .build()

    fun cookieNames(): List<String> = cookieJar.names()

    fun login(login: String, password: String) {
        fetch(AUTH_URL)
        val form = FormBody.Builder()
            .add("action", "login")
            .add("login", login)
            .add("pass", password)
            .build()
        val (code, body) = http.newCall(Request.Builder().url(LOGIN_URL).post(form).build())
            .execute().use { it.code to it.body.string() }
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val librusError = runCatching {
            json?.get("errors")?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        if (librusError != null) throw LibrusException("Librus odrzucił logowanie: $librusError")
        if (code !in 200..299) throw LibrusException("Nie udało się zalogować do Librusa (HTTP $code).")
        loginResponseKeys = json?.keys.orEmpty()
        goTo = runCatching { json?.get("goTo")?.jsonPrimitive?.content }.getOrNull()
        // Librus wskazuje kolejny krok w `goTo` (dziś /OAuth/Authorization/2FA); bez niego — Grant.
        val (grantCode, _) = fetch(goTo?.let(::apiUrl) ?: GRANT_URL)
        if (grantCode !in 200..299) throw LibrusException("Librus nie dokończył logowania (HTTP $grantCode).")
    }

    /** Klucze odpowiedzi na logowanie i adres `goTo` — do diagnostyki w rekonesansie. */
    var loginResponseKeys: Set<String> = emptySet()
        private set
    var goTo: String? = null
        private set

    fun apiUrl(path: String) = if (path.startsWith("http")) path else API_URL + path

    fun gateway(path: String): Pair<Int, String> = fetch(GATEWAY_URL + path)

    fun fetch(url: String): Pair<Int, String> =
        http.newCall(Request.Builder().url(url).build()).execute().use { it.code to it.body.string() }

    private companion object {
        const val AUTH_URL = "https://api.librus.pl/OAuth/Authorization?client_id=46&response_type=code&scope=mydata"
        const val LOGIN_URL = "https://api.librus.pl/OAuth/Authorization?client_id=46"
        const val API_URL = "https://api.librus.pl"
        const val GRANT_URL = "https://api.librus.pl/OAuth/Authorization/Grant?client_id=46"
        const val GATEWAY_URL = "https://synergia.librus.pl/gateway/api/2.0/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Eclipse"
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old ->
            cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path }
        }
        this.cookies += cookies
    }

    @Synchronized
    fun names() = cookies.map { "${it.domain}${it.path}: ${it.name}" }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
}
