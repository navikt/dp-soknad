package no.nav.dagpenger.soknad.minesoknader

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

private val logger = KotlinLogging.logger { }

internal fun Route.mineSoknaderRoute(søknadMediator: SøknadMediator) {
    get("/mine-soknader") {
        val fom = queryParamToFom(call.request.queryParameters["fom"])

        val søknader = søknadMediator.hentSøknader(ident = call.ident())
        val mineSøknaderDto = lagMineSøknaderDto(søknader, fom)

        call.respond(HttpStatusCode.OK, mineSøknaderDto)
    }
}

private fun lagMineSøknaderDto(søknader: Set<Søknad>, fom: LocalDate): MineSoknaderDto {
    var påbegyntSøknad: PåbegyntSøknadDto? = null
    val innsendteSøknader = mutableListOf<InnsendtSøknadDto>()

    søknader.map { søknad ->
        val mineSøknaderVisitor = MineSøknaderVisitor(søknad)
        when {
            mineSøknaderVisitor.søknadTilstand() == Påbegynt && erDagpenger(mineSøknaderVisitor) ->
                påbegyntSøknad = PåbegyntSøknadDto(
                    søknad.søknadUUID(),
                    mineSøknaderVisitor.søknadOpprettet(),
                    mineSøknaderVisitor.sistEndretAvBruker()
                )

            mineSøknaderVisitor.søknadTilstand() == Innsendt && erDagpenger(mineSøknaderVisitor) -> {
                if (mineSøknaderVisitor.førsteInnsendingTidspunkt() > fom.atStartOfDay()) {
                    innsendteSøknader.add(
                        InnsendtSøknadDto(søknad.søknadUUID(), mineSøknaderVisitor.førsteInnsendingTidspunkt())
                    )
                }
            }

            else -> {}
        }
    }
    return MineSoknaderDto(påbegyntSøknad, innsendteSøknader.ifEmpty { null })
}

private fun erDagpenger(mineSøknaderVisitor: MineSøknaderVisitor) =
    mineSøknaderVisitor.prosessversjon().prosessnavn.id == "Dagpenger"

data class MineSoknaderDto(val paabegynt: PåbegyntSøknadDto?, val innsendte: List<InnsendtSøknadDto>?)

data class PåbegyntSøknadDto(val soknadUuid: UUID, val opprettet: LocalDateTime, val sistEndretAvBruker: LocalDateTime?)

data class InnsendtSøknadDto(val soknadUuid: UUID, val forstInnsendt: LocalDateTime)

private fun queryParamToFom(param: String?): LocalDate {
    if (param == null) {
        logger.error("Mangler fom queryparameter i url")
        throw BadRequestException("Mangler fom queryparameter i url")
    }
    return try {
        LocalDate.parse(param)
    } catch (e: DateTimeParseException) {
        logger.error("Kan ikke parse fom queryparameter: $param")
        throw BadRequestException("Kan ikke parse fom queryparameter: $param")
    }
}
