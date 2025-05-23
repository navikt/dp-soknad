package no.nav.dagpenger.soknad.dokumentasjonskrav

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.NotFoundException
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
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.optionalIdent
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import java.time.ZonedDateTime
import java.util.UUID

private val logger = KotlinLogging.logger { }

internal fun Route.dokumentasjonkravRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)

    route("/{søknad_uuid}/dokumentasjonskrav") {
        authenticate("azureAd", "tokenX") {
            get {
                val søknadUuid = søknadUuid()
                withLoggingContext("søknadid" to søknadUuid.toString()) {
                    call.optionalIdent()?.let { ident ->
                        validator.valider(søknadUuid, ident)
                    }
                    val søknad =
                        søknadMediator.hent(søknadUuid) ?: throw NotFoundException("Dokumentasjon ikke funnet")
                    call.respond(ApiDokumentkravResponse(søknad))
                }
            }
        }
        put("/{kravId}/fil") {
            val kravId = call.kravId()
            val ident = call.ident()
            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                validator.valider(søknadUuid, ident)
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
                validator.valider(søknadUuid, ident)
                val svar = call.receive<Svar>()
                søknadMediator.behandle(
                    DokumentasjonIkkeTilgjengelig(
                        søknadUuid,
                        ident,
                        kravId,
                        svar.svar.tilSvarValg(),
                        svar.begrunnelse,
                    ),
                )
                call.respond(HttpStatusCode.Created)
            }
        }
        delete("/{kravId}/fil/{nss...}") {
            val kravId = call.kravId()
            val ident = call.ident()
            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                validator.valider(søknadUuid, ident)
                val urn =
                    URN.rfc8141().parse("urn:vedlegg:${call.nss()}").also {
                        logger.info { "Delete: $it" }
                    }
                søknadMediator.behandle(SlettFil(søknadUuid, ident, kravId, urn))
                call.respond(HttpStatusCode.NoContent)
            }
        }

        put("/{kravId}/bundle") {
            val kravId = call.kravId()
            val ident = call.ident()
            val søknadUuid = søknadUuid()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                validator.valider(søknadUuid, ident)
                val bundleSvar = call.receive<BundleSvar>()
                val dokumentkravSammenstilling =
                    DokumentKravSammenstilling(
                        søknadUuid,
                        ident,
                        kravId,
                        bundleSvar.tilURN(),
                    )
                søknadMediator.behandle(dokumentkravSammenstilling)
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}

private fun ApplicationCall.nss() =
    this.parameters.getAll("nss")?.joinToString("/")
        ?: throw IllegalArgumentException("Fant ikke id for fil")

private fun ApplicationCall.kravId() = this.parameters["kravId"] ?: throw IllegalArgumentException("Mangler kravId")

internal data class ApiFil(
    val filnavn: String,
    val urn: String,
    val storrelse: Long,
    val tidspunkt: ZonedDateTime,
) {
    private val parsedUrn: URN = URN.rfc8141().parse(urn)

    fun tilModell() =
        Krav.Fil(
            filnavn = this.filnavn,
            urn = this.parsedUrn,
            storrelse = this.storrelse,
            tidspunkt = this.tidspunkt,
            bundlet = false,
        )

    override fun toString(): String {
        return "ApiFil(filnavn='*', urn='$urn', storrelse=$storrelse, tidspunkt=$tidspunkt, _urn=$parsedUrn)"
    }
}

private data class Svar(
    val svar: GyldigValg,
    val begrunnelse: String?,
)

private data class BundleSvar(
    val urn: String,
) {
    private val parsedUrn: URN = URN.rfc8141().parse(urn)

    fun tilURN(): URN = parsedUrn
}

@Suppress("PropertyName")
private class ApiDokumentkravResponse(
    søknad: Søknad,
) : SøknadVisitor {
    init {
        søknad.accept(this)
    }

    lateinit var soknad_uuid: UUID
    lateinit var krav: List<ApiDokumentKrav>

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?,
    ) {
        soknad_uuid = søknadId
        krav = dokumentkrav.aktiveDokumentKrav().toApiKrav()
    }

    companion object {
        fun Set<Krav>.toApiKrav(): List<ApiDokumentKrav> =
            map {
                it.innsendt()
                ApiDokumentKrav(
                    id = it.id,
                    beskrivendeId = it.beskrivendeId,
                    beskrivelse = it.beskrivelse,
                    fakta = it.fakta.fold(objectMapper.createArrayNode()) { acc, faktum -> acc.add(faktum.originalJson()) },
                    filer =
                        it.svar.filer.map { fil ->
                            ApiDokumentKrav.ApiDokumentkravFiler(
                                filnavn = fil.filnavn,
                                filsti = fil.urn.namespaceSpecificString().toString(),
                                urn = fil.urn.toString(),
                                storrelse = fil.storrelse,
                                tidspunkt = fil.tidspunkt,
                                bundlet = fil.bundlet,
                            )
                        },
                    bundle = it.svar.bundle?.toString(),
                    bundleFilsti = it.svar.bundle?.namespaceSpecificString()?.toString(),
                    svar = it.svar.valg.fraSvarValg(),
                    begrunnelse = it.svar.begrunnelse,
                )
            }.sortedBy { it.beskrivendeId }
    }
}

data class ApiDokumentKrav(
    val id: String,
    val beskrivendeId: String,
    val beskrivelse: String?,
    val fakta: JsonNode,
    val filer: List<ApiDokumentkravFiler>,
    val gyldigeValg: Set<GyldigValg> = GyldigValg.values().toSet(),
    val begrunnelse: String? = null,
    val svar: GyldigValg? = null,
    val bundle: String? = null,
    val bundleFilsti: String? = null,
) {
    data class ApiDokumentkravFiler(
        val filnavn: String,
        val filsti: String,
        val urn: String,
        val storrelse: Long,
        val tidspunkt: ZonedDateTime,
        val bundlet: Boolean,
    )
}

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
    ANDRE_SENDER,

    ;

    fun tilSvarValg(): Krav.Svar.SvarValg =
        when (this) {
            SEND_NAA -> Krav.Svar.SvarValg.SEND_NÅ
            SEND_SENERE -> Krav.Svar.SvarValg.SEND_SENERE
            SENDT_TIDLIGERE -> Krav.Svar.SvarValg.SEND_TIDLIGERE
            SENDER_IKKE -> Krav.Svar.SvarValg.SENDER_IKKE
            ANDRE_SENDER -> Krav.Svar.SvarValg.ANDRE_SENDER
        }
}

private fun Krav.Svar.SvarValg.fraSvarValg(): GyldigValg? =
    when (this) {
        Krav.Svar.SvarValg.IKKE_BESVART -> null
        Krav.Svar.SvarValg.SEND_NÅ -> GyldigValg.SEND_NAA
        Krav.Svar.SvarValg.SEND_SENERE -> GyldigValg.SEND_SENERE
        Krav.Svar.SvarValg.ANDRE_SENDER -> GyldigValg.ANDRE_SENDER
        Krav.Svar.SvarValg.SEND_TIDLIGERE -> GyldigValg.SENDT_TIDLIGERE
        Krav.Svar.SvarValg.SENDER_IKKE -> GyldigValg.SENDER_IKKE
    }
