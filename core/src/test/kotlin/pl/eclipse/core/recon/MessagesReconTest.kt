package pl.eclipse.core.recon

import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.eclipse.core.source.librus.hidden
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesReconTest {
    @Test
    fun tokenInPathIsHiddenAndQueryDropped() {
        val url = "https://synergia.librus.pl/loguj/token/a1B2c3D4e5F6g7H8i9J0kLmN/przenies?code=tajne".toHttpUrl()
        assertEquals("synergia.librus.pl/loguj/token/‹…›/przenies", url.hidden())
    }

    @Test
    fun loginInMultiDomainLogonIsHidden() {
        // „QUJDMTIzdQ” to zmyślony login zakodowany base64
        val url = "https://wiadomosci.librus.pl/pobierz04/MultiDomainLogon/token/abc123def456/login/QUJDMTIzdQ/target/L25vd3k".toHttpUrl()
        assertEquals("wiadomosci.librus.pl/pobierz04/MultiDomainLogon/token/‹…›/login/‹…›/target/L25vd3k", url.hidden())
    }

    @Test
    fun ordinaryNamesStayReadable() {
        assertEquals("portal.librus.pl/api/v3/SynergiaAccounts", "https://portal.librus.pl/api/v3/SynergiaAccounts".toHttpUrl().hidden())
        assertEquals("portal.librus.pl/konto-librus/login/action", "https://portal.librus.pl/konto-librus/login/action".toHttpUrl().hidden())
    }

    @Test
    fun endpointLikeStringsAreFoundInScript() {
        val script = """a.get("/inbox/messages");b.post(`/outbox/messages/${'$'}{e}`);c="Wyślij wiadomość";d="application/json";g='receivers/groups'"""
        assertEquals(setOf("/inbox/messages", "/outbox/messages/${'$'}{e}", "receivers/groups"), endpointStrings(script))
    }
}
