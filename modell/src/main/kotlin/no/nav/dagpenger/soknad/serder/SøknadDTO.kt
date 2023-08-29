package no.nav.dagpenger.soknad.serder

import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Krav.Svar
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadData
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

class SøknadDTO(
    val søknadsId: UUID,
    val ident: String,
    val tilstandType: TilstandDTO,
    val språkDTO: SpråkDTO,
    var dokumentkrav: DokumentkravDTO,
    val sistEndretAvBruker: ZonedDateTime,
    var aktivitetslogg: AktivitetsloggDTO? = null,
    val opprettet: ZonedDateTime,
    val innsendt: ZonedDateTime?,
    val prosessversjon: ProsessversjonDTO?,
    val data: Lazy<SøknadData>,
) {
    fun rehydrer(): Søknad = Søknad.rehydrer(
        søknadId = this.søknadsId,
        ident = this.ident,
        opprettet = opprettet,
        innsendt = innsendt,
        språk = this.språkDTO.rehydrer(),
        dokumentkrav = this.dokumentkrav.rehydrer(),
        sistEndretAvBruker = this.sistEndretAvBruker,
        tilstandsType = this.tilstandType.rehydrer(),
        aktivitetslogg = aktivitetslogg?.konverterTilAktivitetslogg() ?: Aktivitetslogg(),
        prosessversjon = this.prosessversjon?.rehydrer(),
        data = data,
    )

    class SpråkDTO(val verdi: String) {
        constructor(språk: Locale) : this(språk.toLanguageTag())

        fun rehydrer() = Språk(verdi)
    }

    data class ProsessversjonDTO(
        val prosessnavn: String,
        val versjon: Int,
    ) {
        fun rehydrer() = Prosessversjon(
            prosessnavn = Prosessnavn(this.prosessnavn),
            versjon = this.versjon,
        )
    }

    data class DokumentkravDTO(
        val kravData: Set<KravDTO>,
    ) {
        fun rehydrer(): Dokumentkrav = Dokumentkrav.rehydrer(
            krav = kravData.map { it.rehydrer() }.toSet(),
        )

        data class SannsynliggjøringDTO(
            val id: String,
            val faktum: JsonNode,
            val sannsynliggjør: Set<JsonNode>,
        ) {
            fun rehydrer() = Sannsynliggjøring(
                id = this.id,
                faktum = Faktum(this.faktum),
                sannsynliggjør = this.sannsynliggjør.map { Faktum(it) }.toMutableSet(),
            )
        }

        data class SvarDTO(
            val begrunnelse: String?,
            val filer: Set<KravDTO.FilDTO>,
            val valg: SvarValgDTO,
            val bundle: URN?,
            val innsendt: Boolean,
        ) {
            fun rehydrer() = Svar(
                filer = this.filer.map { it.rehydrer() }.toMutableSet(),
                valg = when (this.valg) {
                    SvarValgDTO.IKKE_BESVART -> Svar.SvarValg.IKKE_BESVART
                    SvarValgDTO.SEND_NÅ -> Svar.SvarValg.SEND_NÅ
                    SvarValgDTO.SEND_SENERE -> Svar.SvarValg.SEND_SENERE
                    SvarValgDTO.ANDRE_SENDER -> Svar.SvarValg.ANDRE_SENDER
                    SvarValgDTO.SEND_TIDLIGERE -> Svar.SvarValg.SEND_TIDLIGERE
                    SvarValgDTO.SENDER_IKKE -> Svar.SvarValg.SENDER_IKKE
                },
                begrunnelse = this.begrunnelse,
                bundle = bundle,
                innsendt = innsendt,
            )

            enum class SvarValgDTO {
                IKKE_BESVART,
                SEND_NÅ,
                SEND_SENERE,
                ANDRE_SENDER,
                SEND_TIDLIGERE,
                SENDER_IKKE,
            }
        }

        data class KravDTO(
            val id: String,
            val beskrivendeId: String,
            val sannsynliggjøring: SannsynliggjøringDTO,
            val tilstand: KravTilstandDTO,
            val svar: SvarDTO,
        ) {
            fun rehydrer() = Krav(
                id = this.id,
                svar = this.svar.rehydrer(),
                sannsynliggjøring = this.sannsynliggjøring.rehydrer(),
                tilstand = when (this.tilstand) {
                    KravTilstandDTO.AKTIV -> Krav.KravTilstand.AKTIV
                    KravTilstandDTO.INAKTIV -> Krav.KravTilstand.INAKTIV
                },
            )

            enum class KravTilstandDTO {
                AKTIV,
                INAKTIV,
            }

            data class FilDTO(
                val filnavn: String,
                val urn: URN,
                val storrelse: Long,
                val tidspunkt: ZonedDateTime,
                val bundlet: Boolean,
            ) {
                fun rehydrer() = Krav.Fil(
                    filnavn = this.filnavn,
                    urn = this.urn,
                    storrelse = this.storrelse,
                    tidspunkt = this.tidspunkt,
                    bundlet = this.bundlet,
                )
            }
        }
    }

    enum class TilstandDTO {
        UnderOpprettelse,
        Påbegynt,
        Innsendt,
        Slettet,
        ;

        fun rehydrer(): Søknad.Tilstand.Type = when (this) {
            UnderOpprettelse -> Søknad.Tilstand.Type.UnderOpprettelse
            Påbegynt -> Søknad.Tilstand.Type.Påbegynt
            Innsendt -> Søknad.Tilstand.Type.Innsendt
            Slettet -> Søknad.Tilstand.Type.Slettet
        }

        companion object {
            fun rehydrer(dbTilstand: String): TilstandDTO {
                return valueOf(dbTilstand)
            }
        }
    }
}
