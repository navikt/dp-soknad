package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.pingPeriod
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.timeout
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import mu.KotlinLogging
import java.time.Duration
import java.util.Collections

private val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(subscribe: (MeldingObserver) -> Unit) {

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val wsConnections = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketSession>())
        webSocket("${Configuration.basePath}/ws") {
            logger.info("WebSocket er åpent for bisniss.")
            wsConnections += WebSocketSession(this).also { subscribe(it) }
            try {
                while (true) {
                    val frame = incoming.receive()
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logger.info { "received text=$text" }
                        }
                        is Frame.Binary -> {
                            logger.info { "received binary" }
                        }
                        is Frame.Close -> {
                            logger.info { "received close" }
                        }
                        is Frame.Ping -> {
                            logger.info { "received ping" }
                        }
                        is Frame.Pong -> {
                            logger.info { "received pong" }
                        }
                    }
                }
            } catch (e : Throwable) {
                logger.error(e) { "Websocket kastet følgende feil: "}
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
