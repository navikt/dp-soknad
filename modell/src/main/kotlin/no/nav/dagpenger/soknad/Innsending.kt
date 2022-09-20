package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingBrevkode
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.BrevkodeMottattHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import java.time.ZonedDateTime
import java.util.UUID

class Innsending private constructor(
    private val innsendingId: UUID,
    private val type: InnsendingType,
    private val innsendt: ZonedDateTime,
    private var journalpostId: String?,
    private var tilstand: Tilstand,
    private var hovedDokument: Dokument? = null,
    private val dokumenter: List<Dokument>,
    private val ettersendinger: MutableList<Innsending>,
    private var brevkode: Brevkode?
) : Aktivitetskontekst {
    private constructor(
        type: InnsendingType,
        innsendt: ZonedDateTime,
        dokumentkrav: Dokumentkrav,
        brevkode: Brevkode? = null
    ) : this(
        innsendingId = UUID.randomUUID(),
        type = type,
        innsendt = innsendt,
        journalpostId = null,
        tilstand = Opprettet,
        dokumenter = dokumentkrav.tilDokument(),
        ettersendinger = mutableListOf(),
        brevkode = brevkode
    )

    companion object {
        fun ny(innsendt: ZonedDateTime, dokumentkrav: Dokumentkrav) =
            Innsending(InnsendingType.NY_DIALOG, innsendt, dokumentkrav)
    }

    data class Dokument(
        val navn: String, // Søknad om dagpenger / Ettersending til søknad om dagpenger
        val brevkode: String, // NAV 04-01.04 / NAVe 04-01.04
        val varianter: List<Dokumentvariant>
    ) {
        data class Dokumentvariant(
            val filnavn: String,
            val urn: String,
            val variant: String, // Arkiv / Original / Fullversjon
            val type: String // PDF / JPG / JSON
        ) {

            init {
                kotlin.runCatching {
                    URN.rfc8141().parse(urn)
                }.onFailure {
                    throw IllegalArgumentException("Ikke gyldig URN: $urn")
                }
            }
        }
    }

    data class Brevkode(val tittel: String, private val skjemakode: String) {
        internal fun brevkode(innsending: Innsending) = when (innsending.type) {
            InnsendingType.NY_DIALOG -> "NAV $skjemakode"
            InnsendingType.ETTERSENDING_TIL_DIALOG -> "NAVe $skjemakode"
        }
    }

    fun ettersend(hendelse: SøknadInnsendtHendelse, dokumentkrav: Dokumentkrav) {
        Innsending(
            InnsendingType.ETTERSENDING_TIL_DIALOG,
            hendelse.innsendtidspunkt(),
            dokumentkrav,
            brevkode
        ).also { ettersending ->
            ettersendinger.add(ettersending)
            ettersending.håndter(hendelse)
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

    private val innsendinger get() = (listOf(this) + ettersendinger)
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

    fun accept(visitor: InnsendingVisitor) {
        visitor.visit(
            innsendingId,
            type,
            tilstand.tilstandType,
            innsendt,
            journalpostId,
            hovedDokument,
            dokumenter
        )
        visitor.preVisitEttersendinger()
        ettersendinger.forEach { it.accept(visitor) }
        visitor.postVisitEttersendinger()
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

    private object Opprettet : Tilstand {
        override val tilstandType = Tilstand.Type.Opprettet

        override fun håndter(hendelse: SøknadInnsendtHendelse, innsending: Innsending) {
            innsending.endreTilstand(AvventerMetadata, hendelse)
            // TODO: DokumentKrav/Ferdigstill må bli med på et vis
        }
    }

    private object AvventerMetadata : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerBrevkode

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            if (innsending.brevkode != null) return innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
            hendelse.behov(
                InnsendingBrevkode,
                "Trenger metadata/klassifisering av innsending"
            )
        }

        override fun håndter(hendelse: BrevkodeMottattHendelse, innsending: Innsending) {
            innsending.brevkode = hendelse.brevkode
            innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
        }
    }

    private object AvventerArkiverbarSøknad : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerArkiverbarSøknad

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            hendelse.behov(
                ArkiverbarSøknad,
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

    private object AvventerMidlertidligJournalføring : Tilstand {
        override val tilstandType = Tilstand.Type.AvventerMidlertidligJournalføring

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            val hovedDokument = requireNotNull(innsending.hovedDokument) { "Hoveddokumment må være satt" }
            val dokumenter = innsending.dokumenter
            hendelse.behov(
                NyJournalpost,
                "Trenger å journalføre søknad",
                mapOf(
                    "hovedDokument" to hovedDokument, // urn til netto/brutto
                    "dokumenter" to dokumenter
                )
            )
        }

        override fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse, innsending: Innsending) {
            innsending.journalpostId = hendelse.journalpostId()
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
}
