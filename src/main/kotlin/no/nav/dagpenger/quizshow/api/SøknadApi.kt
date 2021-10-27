package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import mu.KotlinLogging
import java.util.Collections

val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(subscribe: (MeldingObserver) -> Unit) {
    install(WebSockets)

    routing {
        val wsConnections = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketSession>())

        trace { logger.info { it.buildText() } }
        webSocket("/ws") {
            wsConnections += WebSocketSession(this).also { subscribe(it) }
            try {
                for(frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                        }
                    }
                }
            } finally {
                wsConnections -= this
            }
        }
    }
}

class WebSocketSession(val session: DefaultWebSocketSession) : MeldingObserver, DefaultWebSocketSession by session {
    override suspend fun meldingMottatt(melding: String) {
        outgoing.send(Frame.Text(melding))
    }
}
