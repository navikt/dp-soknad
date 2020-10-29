package no.nav.dagpenger.quizshow.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay


data class SseEvent(val data: String, val event: String? = null, val id: String? = null)

internal fun Application.søknadApi(){
    val channel = produce { // this: ProducerScope<SseEvent> ->
        var n = 0
        while (n < 2) {
            send(SseEvent("demo$n"))
            delay(1000)
            n++
        }
    }.broadcast()
    routing {
        get("/sse"){
            val events = channel.openSubscription()
            try {
                call.respondSse(events)
            } finally {
                events.cancel()
            }
        }
    }
}

suspend fun ApplicationCall.respondSse(events: ReceiveChannel<SseEvent>) {
    response.cacheControl(CacheControl.NoCache(null))
    respondTextWriter(contentType = ContentType.Text.EventStream) {
        for (event in events) {
            if (event.id != null) {
                write("id: ${event.id}\n")
            }
            if (event.event != null) {
                write("event: ${event.event}\n")
            }
            for (dataLine in event.data.lines()) {
                write("data: $dataLine\n")
            }
            write("\n")
            flush()
        }
    }
}
