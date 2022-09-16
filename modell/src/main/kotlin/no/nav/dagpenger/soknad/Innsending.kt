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

class Innsending private constructor(
    private val type: InnsendingType,
    private val innsendt: ZonedDateTime,
    private var journalpost: String?,
    private var tilstand: Tilstand,
    private var hovedDokument: List<Søknad.Journalpost.Variant>? = null,
    private val vedlegg: List<Vedlegg>
) : Aktivitetskontekst {
    private constructor(type: InnsendingType, innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) : this(
        type,
        innsendt,
        null,
        Opprettet,
        // @todo: Vi må ta med alle dokumenkravsvar for å kunne genere søknad PDF med status på vedlegg. Feks "sendes senere"
        vedlegg = dokumentkrav.tilVedlegg()
    )

    companion object {
        fun ny(innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) =
            Innsending(InnsendingType.NY_DIALOG, innsendt, dokumentkrav)

        fun ettersending(innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) =
            Innsending(
                InnsendingType.ETTERSENDING_TIL_DIALOG, innsendt, dokumentkrav
            )
    }

    data class Vedlegg(
        val beskrivendeId: String,
        val urn: URN,
        val type: String // pdf/png/jpg
    )

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
    fun accept(innsendingVisitor: InnsendingVisitor) {
        innsendingVisitor.visit(type, tilstand.tilstandType, innsendt, journalpost, hovedDokument, vedlegg)
    }
    enum class InnsendingType {
        NY_DIALOG,
        ETTERSENDING_TIL_DIALOG
    }
    interface Tilstand : Aktivitetskontekst {
        val tilstandType: Type

        fun entering(hendelse: Hendelse, innsending: Innsending) {}

        fun håndter(hendelse: SøknadInnsendtHendelse, innsending: Innsending) =
            hendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(hendelse: ArkiverbarSøknadMottattHendelse, innsending: Innsending) =
            hendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(
            hendelse: SøknadMidlertidigJournalførtHendelse,
            innsending: Innsending
        ) =
            hendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(hendelse: JournalførtHendelse, innsending: Innsending) =
            hendelse.`kan ikke håndteres i denne tilstanden`()

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

        override fun håndter(hendelse: SøknadInnsendtHendelse, innsending: Innsending) {
            innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
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

        override fun håndter(hendelse: ArkiverbarSøknadMottattHendelse, innsending: Innsending) {
            innsending.hovedDokument = hendelse.dokument().varianter
            innsending.endreTilstand(
                AvventerMidlertidligJournalføring,
                hendelse
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

        override fun håndter(hendelse: JournalførtHendelse, innsending: Innsending) {
            // TODO: Legg til sjekk om at det er DENNE søknaden som er journalført.
            // journalførtHendelse.journalpostId() == innsending.journalpost.id
            innsending.endreTilstand(Journalført, hendelse)
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
