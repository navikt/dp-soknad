package no.nav.dagpenger.soknad.dokumentasjonskrav

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import java.time.ZonedDateTime
import java.util.UUID

private val logger = KotlinLogging.logger { }

internal fun Route.dokumentasjonkravRoute(søknadMediator: SøknadMediator) {

    route("/{søknad_uuid}/dokumentasjonskrav") {
        get {
            val søknadUuid = søknadUuid()
            val ident = call.ident()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                call.respond(ApiDokumentkravResponse(søknadMediator.hent(søknadUuid, ident)))
            }
        }
        put("/{kravId}/fil") {
            val kravId = call.kravId()
            val ident = call.ident()
            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                val fil = call.receive<ApiFil>().also { logger.info { "Received: $it" } }
                søknadMediator.behandle(LeggTilFil(søknadUuid, ident, kravId, fil.tilModell()))
                call.respond(HttpStatusCode.Created)
            }
        }
        put("/{kravId}/svar") {
            val kravId = call.kravId()
            val ident = call.ident()
            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                val svar = call.receive<Svar>()
                søknadMediator.behandle(
                    DokumentasjonIkkeTilgjengelig(
                        søknadUuid,
                        ident,
                        kravId,
                        svar.svar.tilSvarValg(),
                        svar.begrunnelse
                    )
                )
                call.respond(HttpStatusCode.Created)
            }
        }
        delete("/{kravId}/fil/{nss...}") {
            val kravId = call.kravId()
            val ident = call.ident()
            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                val urn = URN.rfc8141().parse("urn:vedlegg:${call.nss()}")
                søknadMediator.behandle(SlettFil(søknadUuid, ident, kravId, urn))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.nss() = this.parameters.getAll("nss")?.joinToString("/")
    ?: throw IllegalArgumentException("Fant ikke id for fil")

private fun ApplicationCall.kravId() = this.parameters["kravId"] ?: throw IllegalArgumentException("Mangler kravId")

internal data class ApiFil(
    val filnavn: String,
    val urn: String,
    val storrelse: Long,
    val tidspunkt: ZonedDateTime
) {
    private val _urn: URN

    init {
        _urn = URN.rfc8141().parse(urn)
    }

    fun tilModell() = Krav.Fil(
        filnavn = this.filnavn,
        urn = this._urn,
        storrelse = this.storrelse,
        tidspunkt = this.tidspunkt,
    )
}

private data class Svar(
    val svar: GyldigValg,
    val begrunnelse: String?
)

private class ApiDokumentkravResponse(
    søknad: Søknad
) : SøknadVisitor {
    init {
        søknad.accept(this)
    }

    lateinit var soknad_uuid: UUID
    lateinit var krav: List<ApiDokumentKrav>

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        soknad_uuid = søknadId
        krav = dokumentkrav.aktiveDokumentKrav().toApiKrav()
    }

    companion object {
        fun Set<Krav>.toApiKrav(): List<ApiDokumentKrav> = map {
            ApiDokumentKrav(
                id = it.id,
                beskrivendeId = it.beskrivendeId,
                fakta = it.fakta.fold(objectMapper.createArrayNode()) { acc, faktum -> acc.add(faktum.json) },
                filer = emptyList(),
            )
        }
    }
}

data class ApiDokumentKrav(
    val id: String,
    val beskrivendeId: String,
    val fakta: JsonNode,
    val filer: List<String>,
    val gyldigeValg: Set<GyldigValg> = GyldigValg.values().toSet(),
    val begrunnelse: String? = null,
    val svar: String? = null
)

enum class GyldigValg {
    @JsonProperty("dokumentkrav.svar.send.naa")
    SEND_NAA,

    @JsonProperty("dokumentkrav.svar.send.senere")
    SEND_SENERE,

    @JsonProperty("dokumentkrav.svar.sendt.tidligere")
    SENDT_TIDLIGERE,

    @JsonProperty("dokumentkrav.svar.sender.ikke")
    SENDER_IKKE,

    @JsonProperty("dokumentkrav.svar.andre.sender")
    ANDRE_SENDER;

    fun tilSvarValg(): Krav.Svar.SvarValg = when (this) {
        SEND_NAA -> Krav.Svar.SvarValg.SEND_NÅ
        SEND_SENERE -> Krav.Svar.SvarValg.SEND_SENERE
        SENDT_TIDLIGERE -> Krav.Svar.SvarValg.SEND_TIDLIGERE
        SENDER_IKKE -> Krav.Svar.SvarValg.SENDER_IKKE
        ANDRE_SENDER -> Krav.Svar.SvarValg.ANDRE_SENDER
    }
}
