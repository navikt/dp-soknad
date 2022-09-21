package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.BrevkodeMottattHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import java.time.ZonedDateTime
import java.util.UUID

abstract class Innsending(
    private val innsendingId: UUID,
    private val type: InnsendingType,
    private val innsendt: ZonedDateTime,
    private var journalpostId: String?,
    private var tilstand: Tilstand,
    private var hovedDokument: Dokument? = null,
    private val dokumenter: List<Dokument>,
    internal var brevkode: Brevkode?
) : Aktivitetskontekst {
    protected open val innsendinger get() = (listOf(this))

    data class Dokument(
        val uuid: UUID = UUID.randomUUID(),
        val navn: String,
        val brevkode: String,
        val varianter: List<Dokumentvariant>
    ) {

        fun toMap(): Map<String, Any> {
            return mapOf(
                "navn" to navn,
                "brevkode" to brevkode,
                "varianter" to varianter.map { it.toMap() }
            )
        }

        data class Dokumentvariant(
            val uuid: UUID = UUID.randomUUID(),
            val filnavn: String,
            val urn: String,
            val variant: String,
            val type: String
        ) {
            init {
                kotlin.runCatching {
                    URN.rfc8141().parse(urn)
                }.onFailure {
                    throw IllegalArgumentException("Ikke gyldig URN: $urn")
                }
            }

            fun toMap(): Map<String, Any> {
                return mapOf(
                    "filnavn" to filnavn,
                    "urn" to urn,
                    "variant" to variant,
                    "type" to type
                )
            }
        }
    }

    data class Brevkode(val tittel: String, private val skjemakode: String) {
        internal fun brevkode(innsending: Innsending) = when (innsending.type) {
            InnsendingType.NY_DIALOG -> "NAV $skjemakode"
            InnsendingType.ETTERSENDING_TIL_DIALOG -> "NAVe $skjemakode"
        }
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

        fun håndter(hendelse: BrevkodeMottattHendelse, innsending: Innsending) =
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
            AvventerBrevkode,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført
        }
    }

    fun håndter(hendelse: SøknadInnsendtHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    fun håndter(hendelse: BrevkodeMottattHendelse) {
        innsendinger.forEach { it._håndter(hendelse) }
    }

    fun håndter(hendelse: ArkiverbarSøknadMottattHendelse) {
        innsendinger.forEach { it._håndter(hendelse) }
    }

    fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse) {
        innsendinger.forEach { it._håndter(hendelse) }
    }

    fun håndter(hendelse: JournalførtHendelse) {
        innsendinger.forEach { it._håndter(hendelse) }
    }

    private fun _håndter(hendelse: BrevkodeMottattHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun _håndter(hendelse: ArkiverbarSøknadMottattHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun _håndter(hendelse: SøknadMidlertidigJournalførtHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun _håndter(hendelse: JournalførtHendelse) {
        if (hendelse.journalpostId() != this.journalpostId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    open fun accept(visitor: InnsendingVisitor) {
        visitor.visit(
            innsendingId,
            type,
            tilstand.tilstandType,
            innsendt,
            journalpostId,
            hovedDokument,
            dokumenter,
            brevkode
        )
    }

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst(
        kontekstType = "innsending",
        mapOf(
            "type" to type.name,
            "innsendingId" to innsendingId.toString()
        )
    )

    private fun endreTilstand(nyTilstand: Tilstand, søknadHendelse: Hendelse) {
        if (nyTilstand == tilstand) {
            return // Vi er allerede i tilstanden
        }
        val forrigeTilstand = tilstand
        tilstand = nyTilstand
        søknadHendelse.kontekst(tilstand)
        tilstand.entering(søknadHendelse, this)
    }

    protected object Opprettet : Tilstand {
        override val tilstandType = Tilstand.Type.Opprettet

        override fun håndter(hendelse: SøknadInnsendtHendelse, innsending: Innsending) {
            innsending.endreTilstand(AvventerMetadata, hendelse)
            // TODO: DokumentKrav/Ferdigstill må bli med på et vis
        }
    }

    protected object AvventerMetadata : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerBrevkode

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            if (innsending.brevkode != null) return innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingBrevkode,
                "Trenger metadata/klassifisering av innsending"
            )
        }

        override fun håndter(hendelse: BrevkodeMottattHendelse, innsending: Innsending) {
            innsending.brevkode = hendelse.brevkode
            innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
        }
    }

    protected object AvventerArkiverbarSøknad : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerArkiverbarSøknad

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad,
                "Trenger søknad på et arkiverbart format",
                mapOf(
                    "innsendtTidspunkt" to innsending.innsendt.toString()
                )
            )
        }

        override fun håndter(hendelse: ArkiverbarSøknadMottattHendelse, innsending: Innsending) {
            val brevkode = requireNotNull(innsending.brevkode) { "Må ha brevkode" }
            innsending.hovedDokument = Dokument(
                navn = brevkode.tittel,
                brevkode = brevkode.brevkode(innsending),
                varianter = hendelse.dokumentvarianter()
            )
            innsending.endreTilstand(
                AvventerMidlertidligJournalføring,
                hendelse
            )
        }
    }

    protected object AvventerMidlertidligJournalføring : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerMidlertidligJournalføring

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            val hovedDokument = requireNotNull(innsending.hovedDokument) { "Hoveddokumment må være satt" }
            val dokumenter = innsending.dokumenter
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost,
                "Trenger å journalføre søknad",
                mapOf(
                    "hovedDokument" to hovedDokument.toMap(), // urn til netto/brutto
                    "dokumenter" to dokumenter.map { it.toMap() }
                )
            )
        }

        override fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse, innsending: Innsending) {
            innsending.journalpostId = hendelse.journalpostId()
            innsending.endreTilstand(AvventerJournalføring, hendelse)
        }
    }

    protected object AvventerJournalføring : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerJournalføring

        override fun håndter(hendelse: JournalførtHendelse, innsending: Innsending) {
            // TODO: Legg til sjekk om at det er DENNE søknaden som er journalført.
            // journalførtHendelse.journalpostId() == innsending.journalpost.id
            innsending.endreTilstand(Journalført, hendelse)
        }
    }

    protected object Journalført : Tilstand {
        override val tilstandType = Tilstand.Type.Journalført
    }

    companion object {
        fun ny(innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) =
            NyInnsending(InnsendingType.NY_DIALOG, innsendt, dokumentkrav)
    }
}
