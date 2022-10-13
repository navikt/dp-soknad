package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.ZonedDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Route.påbegyntSøknadRoute(søknadMediator: SøknadMediator) {
    get("/paabegynt") {
        try {
            val påbegyntSøknad = søknadMediator.hentPåbegyntSøknad(call.ident())?.let {
                PåbegynteSøknadVisitor(it).påbegyntSøknad
            }

            if (påbegyntSøknad != null) {
                logger.info { "$påbegyntSøknad returnert" }
                call.respond(HttpStatusCode.OK, påbegyntSøknad)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            logger.error { "Noe gikk galt med henting av påbegynt søknad: $e" }
        }
    }
}

private class PåbegynteSøknadVisitor(søknad: Søknad) : SøknadVisitor {

    lateinit var påbegyntSøknad: PåbegyntSøknad
    init {
        søknad.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        påbegyntSøknad = PåbegyntSøknad(
            uuid = søknadId,
            opprettet = ZonedDateTime.now(),
            spraak = språk.verdi.toLanguageTag(),
        )
    }
}

private data class PåbegyntSøknad(
    val uuid: UUID,
    val opprettet: ZonedDateTime,
    val spraak: String
)
