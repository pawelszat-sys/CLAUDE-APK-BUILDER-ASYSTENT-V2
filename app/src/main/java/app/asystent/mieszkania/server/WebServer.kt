package app.asystent.mieszkania.server

import app.asystent.mieszkania.Hub
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject

/** Mały serwer WWW: strona z widokiem aplikacji i proste API. PIN (jeśli ustawiony) dotyczy API. */
class WebServer(private val hub: Hub, private val page: String, port: Int) : NanoHTTPD(port) {

    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/" || uri == "/index.html") {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page)
        }
        if (!uri.startsWith("/api/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Nie znaleziono")
        }
        if (!hub.pinOk(session.headers["x-pin"])) {
            return json(Response.Status.UNAUTHORIZED, "{\"error\":\"pin\"}")
        }
        val post = session.method == Method.POST
        when (uri) {
            "/api/state" -> Unit
            "/api/light/toggle" -> if (post) hub.toggleLight()
            "/api/advice/refresh" -> if (post) hub.refreshAdvice(true)
            "/api/refresh" -> if (post) hub.refreshOutdoor(true)
            "/api/ask" -> if (post) {
                val files = HashMap<String, String>()
                try {
                    session.parseBody(files)
                    val q = JSONObject(files["postData"] ?: "{}").optString("q")
                    hub.ask(q, false)
                } catch (e: Exception) {
                    return json(Response.Status.BAD_REQUEST, "{\"error\":\"zle zapytanie\"}")
                }
            }
            "/api/history" -> return json(Response.Status.OK, hub.historyJson())
            "/api/source" -> if (post) {
                val files = HashMap<String, String>()
                try {
                    session.parseBody(files)
                    hub.setOutdoorSource(JSONObject(files["postData"] ?: "{}").optString("s"))
                } catch (e: Exception) {
                    return json(Response.Status.BAD_REQUEST, "{\"error\":\"zle zapytanie\"}")
                }
            }
            else -> return json(Response.Status.NOT_FOUND, "{\"error\":\"brak\"}")
        }
        return json(Response.Status.OK, hub.stateJson())
    }
}
