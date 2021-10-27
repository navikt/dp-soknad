package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.StatusPages
import io.ktor.response.respond
import io.ktor.routing.put
import io.ktor.routing.routing

internal fun Application.demoApi(publiser: (String) -> Unit, env: Map<String, String>) {

    install(StatusPages)

    routing {
        put(path = "/demo/{fnr}") {
            assertNotProd(env)
            val fnr = this.call.parameters["fnr"] ?: throw IllegalArgumentException("Må sette parameter til fnr")
            publiser(fnr)
            call.respond("$fnr publisert til rapid")
        }
    }
}

private fun assertNotProd(env: Map<String, String>) {
    if (env["NAIS_CLUSTER"] != "dev-gcp")
        throw IllegalArgumentException("Skal ikke publisere demo hendelser i prod")
}
