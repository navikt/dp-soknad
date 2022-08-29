package no.nav.dagpenger.soknad.dokumentasjonskrav

import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger { }

internal fun Route.dokumentasjonkravRoute(søknadRepository: SøknadRepository) {
    route("/{søknad_uuid}/dokumentasjonskrav") {
        get {
            val søknadUuid = søknadUuid()
            val ident = call.ident()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                val dokumentkrav = søknadRepository.hentDokumentkravFor(søknadUuid, ident)
                val apiDokumentkravResponse = ApiDokumentkravResponse(
                    soknad_uuid = søknadUuid,
                    krav = dokumentkrav.aktiveDokumentKrav().toApiKrav()
                )
                call.respond(apiDokumentkravResponse)
            }
        }
        put("/{kravId}/fil") {

            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {

                // hentdokumentkrav
                val fil = call.receive<ApiFil>().let { logger.info { "Received: $it" } }
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}
private data class ApiFil(
    val filnavn: String,
    val urn: String,
    val storrelse: BigInteger,
    val tidspunkt: LocalDateTime
) {
    init {
        URN.rfc8141().parse(urn)
    }
}

private data class ApiDokumentkravResponse(
    val soknad_uuid: UUID,
    val krav: List<ApiDokumentKrav>,
)
fun Set<Krav>.toApiKrav(): List<ApiDokumentKrav> = map {
    ApiDokumentKrav(
        id = it.id,
        beskrivendeId = it.beskrivendeId,
        fakta = it.fakta.fold(objectMapper.createArrayNode()) { acc, faktum -> acc.add(faktum.json) },
        filer = emptyList(),
    )
}

data class ApiDokumentKrav(
    val id: String,
    val beskrivendeId: String,
    val fakta: JsonNode,
    val filer: List<String>,
    val gyldigeValg: Set<String> = setOf(
        "dokumentkrav.svar.send.naa",
        "dokumentkrav.svar.send.senere",
        "dokumentkrav.svar.send.noen_andre",
        "dokumentkrav.svar.sendt.tidligere",
        "dokumentkrav.svar.sender.ikke",
    ),
    val begrunnelse: String? = null,
    val svar: String? = null
)
