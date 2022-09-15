package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import java.time.ZonedDateTime

internal class Innsending private constructor(
    private val type: InnsendingType,
    private val innsendt: ZonedDateTime,
    private var journalpost: String?,
    private var tilstand: Tilstand,
    private var hovedDokument: List<Søknad.Journalpost.Variant>? = null,
    private val vedlegg: List<Vedlegg>
) : Aktivitetskontekst {

    companion object {
        fun ny(innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) =
            Innsending(InnsendingType.NY_DIALOG, innsendt, dokumentkrav)

        fun ettersending(innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) =
            Innsending(
                InnsendingType.ETTERSENDING_TIL_DIALOG, innsendt, dokumentkrav)
    }

    private constructor(type: InnsendingType, innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) : this(
        type,
        innsendt,
        null,
        Opprettet,
        vedlegg = dokumentkrav.tilVedlegg()
    )

    data class Vedlegg(
        val beskrivendeId: String,
        val urn: URN,
        val type: String // pdf/png/jpg
    )

    enum class InnsendingType {
        NY_DIALOG,
        ETTERSENDING_TIL_DIALOG
    }

    fun håndter(hendelse: SøknadInnsendtHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    fun håndter(hendelse: ArkiverbarSøknadMottattHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }


    fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }


    fun håndter(hendelse: JournalførtHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    interface Tilstand : Aktivitetskontekst {
        val tilstandType: Type

        fun entering(hendelse: Hendelse, innsending: Innsending) {}

        fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, innsending: Innsending) =
            søknadInnsendtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, innsending: Innsending) =
            arkiverbarSøknadMotattHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(
            søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse,
            innsending: Innsending
        ) =
            søknadMidlertidigJournalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(journalførtHendelse: JournalførtHendelse, innsending: Innsending) =
            journalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        private fun Hendelse.`kan ikke håndteres i denne tilstanden`() =
            this.warn("Kan ikke håndtere ${this.javaClass.simpleName} i tilstand $tilstandType")

        override fun toSpesifikkKontekst() = this.javaClass.canonicalName.split('.').last().let {
            SpesifikkKontekst(it, emptyMap())
        }

        enum class Type {
            Opprettet,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført
        }
    }

    private object Opprettet : Tilstand {
        override val tilstandType = Tilstand.Type.Opprettet

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, innsending: Innsending) {
            innsending.endreTilstand(AvventerArkiverbarSøknad, søknadInnsendtHendelse)
            // TODO: DokumentKrav/Ferdigstill må bli med på et vis
            // TODO: Frontend må bundle hvert dokumentkrav
        }
    }

    private object AvventerArkiverbarSøknad : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerArkiverbarSøknad

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            hendelse.behov(
                ArkiverbarSøknad,
                "Trenger søknad på et arkiverbart format",
                mapOf(
                    "innsendtTidspunkt" to innsending.innsendt.toString(),
                    "type" to innsending.type
                )
            )
        }

        override fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, innsending: Innsending) {
            innsending.hovedDokument = arkiverbarSøknadMotattHendelse.dokument().varianter
            innsending.endreTilstand(
                AvventerMidlertidligJournalføring,
                arkiverbarSøknadMotattHendelse
            )
        }
    }

    private object AvventerMidlertidligJournalføring : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerMidlertidligJournalføring

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            val hovedDokument = requireNotNull(innsending.hovedDokument) { "Hoveddokumment må være satt" }
            val vedlegg = listOf<Vedlegg>()
            hendelse.behov(
                NyJournalpost,
                "Trenger å journalføre søknad",
                mapOf(
                    "hovedDokument" to hovedDokument, // urn til netto/brutto
                    "vedlegg" to vedlegg,
                    "type" to innsending.type
                )
            )
        }

        override fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse, innsending: Innsending) {
            innsending.journalpost = hendelse.journalpostId()
            innsending.endreTilstand(AvventerJournalføring, hendelse)
        }
    }

    private object AvventerJournalføring : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerJournalføring

        override fun håndter(journalførtHendelse: JournalførtHendelse, innsending: Innsending) {
            // TODO: Legg til sjekk om at det er DENNE søknaden som er journalført.
            // journalførtHendelse.journalpostId() == innsending.journalpost.id
            innsending.endreTilstand(Journalført, journalførtHendelse)
        }
    }

    private object Journalført : Tilstand {
        override val tilstandType = Tilstand.Type.Journalført
    }

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst(kontekstType = "innsending", mapOf("type" to type.name))

    private fun endreTilstand(nyTilstand: Tilstand, søknadHendelse: Hendelse) {
        if (nyTilstand == tilstand) {
            return // Vi er allerede i tilstanden
        }
        val forrigeTilstand = tilstand
        tilstand = nyTilstand
        søknadHendelse.kontekst(tilstand)
        tilstand.entering(søknadHendelse, this)
    }

}
