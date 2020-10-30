package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import mu.KotlinLogging
import java.util.Collections

val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(mediator: Mediator) {

    install(WebSockets)

    routing {
        val wsConnections = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketSession>())

        trace { logger.info { it.buildText() } }
        webSocket("/arbeid/dagpenger/websockets/chat") {
            wsConnections += this
            try {
                while (true) {
                    val frame = incoming.receive() // suspend
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            outgoing.send(Frame.Text(text)) // suspend
                        }
                    }
                }
            } finally {
                wsConnections -= this
            }
        }
    }
}



