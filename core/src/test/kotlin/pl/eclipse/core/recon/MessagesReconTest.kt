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
    fun ordinaryNamesStayReadable() {
        assertEquals("portal.librus.pl/api/v3/SynergiaAccounts", "https://portal.librus.pl/api/v3/SynergiaAccounts".toHttpUrl().hidden())
    }

    @Test
    fun apiPathsAreFoundInScript() {
        assertEquals(setOf("/api/inbox/messages", "/api/me"), apiPaths("""fetch("/api/me");x='/api/inbox/messages/';y="/static/app.js""""))
    }
}
