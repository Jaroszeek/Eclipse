package pl.eclipse.core.source.librus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class LibrusException(message: String) : Exception(message)

/** Konto ucznia z listy kont Konta LIBRUS (bez loginu i nazwiska — tylko to, co potrzebne). */
data class SynergiaAccount(val group: String?, val state: String?, val accessToken: String?)

/**
 * Logowanie przez Konto LIBRUS (e-mail), tak jak aplikacja mobilna Librusa:
 * portal → kod → token portalu → lista kont ucznia → token do API `api.librus.pl/2.0`.
 */
class LibrusClient {
    private val cookieJar = MemoryCookieJar()

    /** Przebieg zapytań do diagnostyki: metoda, host, ścieżka (bez parametrów) i kod odpowiedzi. */
    val trace = mutableListOf<String>()

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false) // przekierowania obsługujemy sami, żeby złapać app://librus?code=
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

    var accounts: List<SynergiaAccount> = emptyList()
        private set
    private var apiToken: String? = null

    fun cookieNames(): List<String> = cookieJar.names()

    fun login(email: String, password: String) {
        val portalToken = accessToken(authorizationCode(email, password))
        val (code, body) = send(Request.Builder().url(ACCOUNTS_URL).header("Authorization", "Bearer $portalToken").build())
        if (code !in 200..299) throw LibrusException("Portal nie podał listy kont (HTTP $code).")
        accounts = runCatching {
            Json.parseToJsonElement(body).jsonObject["accounts"]?.jsonArray.orEmpty().map {
                val o = it.jsonObject
                SynergiaAccount(o.text("group"), o.text("state"), o.text("accessToken"))
            }
        }.getOrElse { throw LibrusException("Nie udało się odczytać listy kont z portalu.") }
        apiToken = (accounts.firstOrNull { it.group != "parent" } ?: accounts.firstOrNull())?.accessToken
            ?: throw LibrusException("Do Konta LIBRUS nie jest dodane żadne konto ucznia w Synergii.")
    }

    /** Zasób API Librusa, np. "Grades" → https://api.librus.pl/2.0/Grades. */
    fun gateway(path: String): Pair<Int, String> =
        send(Request.Builder().url(API_URL + path).header("Authorization", "Bearer $apiToken").build())

    private fun authorizationCode(email: String, password: String): String {
        var url = AUTHORIZE_URL
        var referer: String? = null
        var loginSent = false
        repeat(MAX_STEPS) {
            val (response, body) = request(url, referer)
            val location = response.header("Location")
            if (location != null) {
                CODE.find(location)?.let { return it.groupValues[1] }
                if ("rejected_client" in location) throw LibrusException("Portal Librusa odrzucił aplikację (rejected_client).")
                if ("command=close" in location) throw LibrusException("Portal Librusa ma przerwę techniczną. Spróbuj później.")
                url = url.toHttpUrl().resolve(location)?.toString() ?: throw LibrusException("Nieprawidłowe przekierowanie z portalu.")
                return@repeat
            }
            if (loginSent) {
                diagnostics += "Strona po logowaniu: ${describePage(body)}"
                pageError(body)?.let { throw LibrusException(it) }
                throw LibrusException("Portal nie przekazał kodu logowania (HTTP ${response.code}).")
            }

            captchaCheck(body)
            diagnostics += "Strona logowania: ${describePage(body)}"
            val form = FormBody.Builder().add("email", email).add("password", password)
            HIDDEN_INPUT.findAll(body).forEach { input ->
                val name = INPUT_NAME.find(input.value)?.groupValues?.get(1)
                val value = INPUT_VALUE.find(input.value)?.groupValues?.get(1)
                if (name != null && value != null) form.add(name, value)
            }
            val post = Request.Builder().url(LOGIN_URL).post(form.build())
                .header("Referer", url).header("X-Requested-With", APP_HEADER)
            CSRF.find(body)?.let { post.header("X-CSRF-TOKEN", it.groupValues[1]) }
            val (postResponse, postBody) = execute(post)
            loginSent = true
            if (postResponse.code != 302) {
                diagnostics += "Odpowiedź na formularz (HTTP ${postResponse.code}): ${describePage(postBody)}"
                pageError(postBody)?.let { throw LibrusException(it) }
            }
            val next = postResponse.header("Location")
            if (next != null) {
                CODE.find(next)?.let { return it.groupValues[1] }
                if ("command=close" in next) throw LibrusException("Portal Librusa ma przerwę techniczną. Spróbuj później.")
            }
            referer = url
            url = next?.let { LOGIN_URL.toHttpUrl().resolve(it)?.toString() } ?: AUTHORIZE_URL
        }
        throw LibrusException("Za dużo przekierowań podczas logowania do portalu.")
    }

    private fun accessToken(code: String): String {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        val (status, body) = send(Request.Builder().url(TOKEN_URL).post(form).build())
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        return json?.text("access_token")
            ?: throw LibrusException("Portal nie wydał tokenu (HTTP $status: ${json?.text("hint") ?: json?.text("error")}).")
    }

    /** Opis strony do diagnostyki: pola formularza (bez wartości) i komunikaty błędów portalu. */
    val diagnostics = mutableListOf<String>()

    private fun describePage(body: String): String {
        val fields = INPUT.findAll(body).map { input ->
            val name = INPUT_NAME.find(input.value)?.groupValues?.get(1)
            val type = INPUT_TYPE.find(input.value)?.groupValues?.get(1) ?: "text"
            "$name($type)"
        }.toList()
        val forms = FORM.findAll(body).map { it.groupValues[1].substringBefore('?') }.toList()
        val errors = ERROR_BLOCK.findAll(body).map { TAG.replace(it.groupValues[1], " ").replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }.map { it.take(160) }.distinct().toList()
        return "formularze: $forms; pola: $fields; komunikaty: $errors; " +
            "„Upewnij się, że nie”: ${"Upewnij się, że nie" in body}; csrf: ${CSRF.containsMatchIn(body)}"
    }

    private fun captchaCheck(body: String) {
        if ("g-recaptcha" in body || "captchaValidate" in body) throw LibrusException(
            "Portal Librusa prosi o potwierdzenie, że nie jesteś robotem. Zaloguj się raz na portal.librus.pl w przeglądarce i spróbuj ponownie."
        )
    }

    private fun pageError(body: String): String? = when {
        "Sesja logowania wygasła" in body -> "Sesja logowania wygasła. Spróbuj jeszcze raz."
        "Upewnij się, że nie" in body || "Podany adres e-mail jest nieprawidłowy." in body ->
            "Nieprawidłowy e-mail lub hasło do Konta LIBRUS."
        else -> null
    }

    private fun request(url: String, referer: String?) = execute(
        Request.Builder().url(url).header("X-Requested-With", APP_HEADER)
            .also { if (referer != null) it.header("Referer", referer) }
    )

    private fun execute(builder: Request.Builder) =
        http.newCall(builder.build()).execute().use { it to it.body.string() }

    private fun send(request: Request): Pair<Int, String> =
        http.newCall(request).execute().use { it.code to it.body.string() }

    private fun JsonObject.text(key: String) = runCatching { get(key)?.jsonPrimitive?.content }.getOrNull()

    private companion object {
        // Identyfikator i adresy aplikacji mobilnej Librusa (za szkolny-android, Constants.kt).
        const val CLIENT_ID = "VaItV6oRutdo8fnjJwysnTjVlvaswf52ZqmXsJGP"
        const val REDIRECT_URI = "app://librus"
        const val APP_HEADER = "pl.librus.synergiaDru2"
        const val AUTHORIZE_URL = "https://portal.librus.pl/konto-librus/redirect/dru"
        const val LOGIN_URL = "https://portal.librus.pl/konto-librus/login/action"
        const val TOKEN_URL = "https://portal.librus.pl/oauth2/access_token"
        const val ACCOUNTS_URL = "https://portal.librus.pl/api/v3/SynergiaAccounts"
        const val API_URL = "https://api.librus.pl/2.0/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) LibrusMobileApp"
        const val MAX_STEPS = 10
        val CODE = Regex("""app://librus\?code=([^&?]+)""")
        val CSRF = Regex("""name="csrf-token" content="([A-Za-z0-9=+/\-_]+?)"""")
        val INPUT = Regex("""<input [^>]*>""")
        val INPUT_TYPE = Regex("""type="(.+?)"""")
        val FORM = Regex("""<form [^>]*action="(.+?)"""")
        val ERROR_BLOCK = Regex("""<[^>]+class="[^"]*(?:error|invalid|alert|danger)[^"]*"[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
        val TAG = Regex("""<[^>]+>""")
        val SPACES = Regex("""\s+""")
        val HIDDEN_INPUT = Regex("""<input [^>]*type="hidden"[^>]*>""")
        val INPUT_NAME = Regex("""name="(.+?)"""")
        val INPUT_VALUE = Regex("""value="(.*?)"""")
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
