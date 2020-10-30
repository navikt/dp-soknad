package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket

internal fun Application.søknadApi() {
    install(WebSockets)

    routing {
        webSocket("/chat") {
            while (true) {
                val frame = incoming.receive() // suspend
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        outgoing.send(Frame.Text(text)) // suspend
                    }
                }
            }
        }
    }
}
