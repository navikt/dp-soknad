package no.nav.dagpenger.soknad.minesoknader

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Companion.erDagpenger
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
        val includeDokumentkrav = call.request.queryParameters["include"] == "dokumentkrav"

        val dagpengeSøknader = søknadMediator.hentSøknader(ident = call.ident()).filter { it.erDagpenger() }
        val mineSøknaderDto = lagMineSøknaderDto(dagpengeSøknader, fom, includeDokumentkrav)

        call.respond(HttpStatusCode.OK, mineSøknaderDto)
    }
}

private fun lagMineSøknaderDto(søknader: List<Søknad>, fom: LocalDate, includeDokumentkrav: Boolean): MineSoknaderDto {
    var påbegyntSøknad: PåbegyntSøknadDto? = null
    val innsendteSøknader = mutableListOf<InnsendtSøknadDto>()

    søknader.map { søknad ->
        val mineSøknaderVisitor = MineSøknaderVisitor(søknad)
        val dokumentkrav = if (includeDokumentkrav) {
            mineSøknaderVisitor.dokumentkrav().toMineSoknaderDokumentkravDTO()
        } else null

        when {
            mineSøknaderVisitor.søknadTilstand() == Påbegynt ->
                påbegyntSøknad = PåbegyntSøknadDto(
                    søknad.søknadUUID(),
                    mineSøknaderVisitor.søknadOpprettet(),
                    mineSøknaderVisitor.sistEndretAvBruker()
                )

            mineSøknaderVisitor.søknadTilstand() == Innsendt -> {
                if (mineSøknaderVisitor.søknadInnsendt().toLocalDate() > fom) {
                    innsendteSøknader.add(
                        InnsendtSøknadDto(
                            søknad.søknadUUID(),
                            mineSøknaderVisitor.søknadInnsendt(),
                            dokumentkrav
                        )
                    )
                }
            }

            else -> {}
        }
    }
    return MineSoknaderDto(påbegyntSøknad, innsendteSøknader.ifEmpty { null })
}

internal fun Set<Krav>.toMineSoknaderDokumentkravDTO(): List<MineSoknaderDokumentkravDTO> = map { krav ->
    MineSoknaderDokumentkravDTO(
        id = krav.id,
        beskrivendeId = krav.beskrivendeId,
        begrunnelse = krav.svar.begrunnelse,
        filer = krav.svar.filer.map { it.filnavn },
        valg = krav.svar.valg.name,
        bundleFilsti = krav.svar.bundle?.namespaceSpecificString()?.toString()
    )
}

internal data class MineSoknaderDto(val paabegynt: PåbegyntSøknadDto?, val innsendte: List<InnsendtSøknadDto>?)

internal data class PåbegyntSøknadDto(
    val soknadUuid: UUID,
    val opprettet: LocalDateTime,
    val sistEndretAvBruker: LocalDateTime?,
)

internal data class InnsendtSøknadDto(
    val soknadUuid: UUID,
    val forstInnsendt: LocalDateTime,
    val dokumentkrav: List<MineSoknaderDokumentkravDTO>?,
)

internal data class MineSoknaderDokumentkravDTO(
    val id: String,
    val beskrivendeId: String,
    val begrunnelse: String?,
    val filer: List<String>,
    val valg: String,
    val bundleFilsti: String?,
)

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
