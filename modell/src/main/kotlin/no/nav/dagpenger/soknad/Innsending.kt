package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.innsending.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingHendelse
import java.time.ZonedDateTime
import java.util.UUID

class Innsending private constructor(
    val innsendingId: UUID,
    private val ident: String,
    private val søknadId: UUID,
    private val type: InnsendingType,
    private val innsendt: ZonedDateTime,
    private var journalpostId: String?,
    private var tilstand: Tilstand,
    private var hovedDokument: Dokument? = null,
    private val dokumenter: List<Dokument>,
    internal var metadata: Metadata?
) : Aktivitetskontekst {
    private val innsendinger get() = (listOf(this))
    private val observers = mutableListOf<InnsendingObserver>()

    internal constructor(
        type: InnsendingType,
        ident: String,
        søknadId: UUID,
        innsendt: ZonedDateTime,
        dokumentkrav: List<Dokument>,
        metadata: Metadata? = null
    ) : this(
        innsendingId = UUID.randomUUID(),
        ident = ident,
        søknadId = søknadId,
        type = type,
        innsendt = innsendt,
        journalpostId = null,
        tilstand = Opprettet,
        dokumenter = dokumentkrav,
        metadata = metadata
    )

    companion object {

        fun ny(innsendt: ZonedDateTime, ident: String, søknadId: UUID, dokumentkrav: List<Dokument>) =
            Innsending(InnsendingType.NY_DIALOG, ident, søknadId, innsendt, dokumentkrav)

        fun ettersending(innsendt: ZonedDateTime, ident: String, søknadId: UUID, dokumentkrav: List<Dokument>) =
            Innsending(InnsendingType.ETTERSENDING_TIL_DIALOG, ident, søknadId, innsendt, dokumentkrav)

        fun rehydrer(
            innsendingId: UUID,
            type: InnsendingType,
            ident: String,
            søknadId: UUID,
            innsendt: ZonedDateTime,
            journalpostId: String?,
            tilstandsType: TilstandType,
            hovedDokument: Dokument? = null,
            dokumenter: List<Dokument>,
            metadata: Metadata?
        ): Innsending {
            val tilstand: Tilstand = when (tilstandsType) {
                TilstandType.Opprettet -> Opprettet
                TilstandType.AvventerMetadata -> AvventerMetadata
                TilstandType.AvventerArkiverbarSøknad -> AvventerArkiverbarSøknad
                TilstandType.AvventerMidlertidligJournalføring -> AvventerMidlertidligJournalføring
                TilstandType.AvventerJournalføring -> AvventerJournalføring
                TilstandType.Journalført -> Journalført
            }
            return Innsending(
                innsendingId = innsendingId,
                ident = ident,
                søknadId = søknadId,
                type = type,
                innsendt = innsendt,
                journalpostId = journalpostId,
                tilstand = tilstand,
                hovedDokument = hovedDokument,
                dokumenter = dokumenter,
                metadata = metadata
            )
        }
    }

    fun håndter(hendelse: NyInnsendingHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    fun håndter(hendelse: InnsendingMetadataMottattHendelse) {
        innsendinger.forEach { it._håndter(hendelse) }
    }

    fun håndter(hendelse: ArkiverbarSøknadMottattHendelse) {
        kontekst(hendelse)
        hendelse.info("Arkiverbar søknad mottatt")
        if (!hendelse.valider()) {
            hendelse.warn("Ikke gyldig dokumentlokasjon")
            return
        }
        innsendinger.forEach { it._håndter(hendelse) }
    }

    fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(hendelse)
        hendelse.info("Søknad midlertidig journalført")
        innsendinger.forEach { it._håndter(hendelse) }
    }

    fun håndter(hendelse: JournalførtHendelse) {
        kontekst(hendelse)
        hendelse.info("Søknad journalført")
        innsendinger.forEach { it._håndter(hendelse) }
    }

    private fun _håndter(hendelse: InnsendingMetadataMottattHendelse) {
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

    private fun endreTilstand(nyTilstand: Tilstand, søknadHendelse: Hendelse) {
        if (nyTilstand == tilstand) {
            return // Vi er allerede i tilstanden
        }
        val forrigeTilstand = tilstand
        tilstand = nyTilstand
        søknadHendelse.kontekst(tilstand)
        tilstand.entering(søknadHendelse, this)
        varsleOmEndretTilstand(forrigeTilstand)
    }

    private fun varsleOmEndretTilstand(forrigeTilstand: Tilstand) {
        observers.forEach {
            it.innsendingTilstandEndret(
                InnsendingObserver.InnsendingEndretTilstandEvent(
                    innsendingId = innsendingId,
                    innsendingType = type,
                    gjeldendeTilstand = tilstand.tilstandType,
                    forrigeTilstand = forrigeTilstand.tilstandType
                )
            )
        }
    }

    private interface Tilstand : Aktivitetskontekst {
        val tilstandType: TilstandType

        fun entering(hendelse: Hendelse, innsending: Innsending) {}

        fun håndter(hendelse: NyInnsendingHendelse, innsending: Innsending) =
            hendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(hendelse: InnsendingMetadataMottattHendelse, innsending: Innsending) =
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
    }

    private object Opprettet : Tilstand {
        override val tilstandType = TilstandType.Opprettet

        override fun håndter(hendelse: NyInnsendingHendelse, innsending: Innsending) {
            hendelse.info("Innsending ${innsending.toSpesifikkKontekst()} med id ${innsending.innsendingId} opprettet.")
            innsending.endreTilstand(AvventerMetadata, hendelse)
        }
    }

    private object AvventerMetadata : Tilstand {
        override val tilstandType = TilstandType.AvventerMetadata

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            if (innsending.metadata != null) return innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingMetadata,
                "Trenger metadata/klassifisering av innsending"
            )
        }

        override fun håndter(hendelse: InnsendingMetadataMottattHendelse, innsending: Innsending) {
            innsending.metadata = hendelse.metadata
            innsending.endreTilstand(AvventerArkiverbarSøknad, hendelse)
        }
    }

    private object AvventerArkiverbarSøknad : Tilstand {
        override val tilstandType = TilstandType.AvventerArkiverbarSøknad

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            val metadata = requireNotNull(innsending.metadata) { "Må ha metadata" }
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad,
                "Trenger søknad på et arkiverbart format",
                mapOf(
                    "innsendtTidspunkt" to innsending.innsendt.toString(),
                    "dokumentasjonKravId" to innsending.dokumenter.map { it.kravId },
                    "skjemakode" to metadata.skjemakode
                )
            )
        }

        override fun håndter(hendelse: ArkiverbarSøknadMottattHendelse, innsending: Innsending) {
            val metadata = requireNotNull(innsending.metadata) { "Må ha metadata" }
            innsending.hovedDokument = Dokument(
                kravId = null,
                skjemakode = metadata.skjemakode,
                varianter = hendelse.dokumentvarianter()
            )
            innsending.endreTilstand(
                AvventerMidlertidligJournalføring,
                hendelse
            )
        }
    }

    private object AvventerMidlertidligJournalføring : Tilstand {
        override val tilstandType = TilstandType.AvventerMidlertidligJournalføring

        override fun entering(hendelse: Hendelse, innsending: Innsending) {
            val hovedDokument = requireNotNull(innsending.hovedDokument) { "Hoveddokument må være satt" }
            val dokumenter = innsending.dokumenter
            hendelse.info("Lager journalpost med dokumenter=${dokumenter.size}")
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

    private object AvventerJournalføring : Tilstand {
        override val tilstandType = TilstandType.AvventerJournalføring

        override fun håndter(hendelse: JournalførtHendelse, innsending: Innsending) {
            if (hendelse.journalpostId() == innsending.journalpostId) {
                innsending.endreTilstand(Journalført, hendelse)
            }
        }
    }

    private object Journalført : Tilstand {
        override val tilstandType = TilstandType.Journalført
    }

    fun accept(visitor: InnsendingVisitor) {
        visitor.visit(
            innsendingId = innsendingId,
            søknadId = søknadId,
            ident = ident,
            innsendingType = type,
            tilstand = tilstand.tilstandType,
            innsendt = innsendt,
            journalpost = journalpostId,
            hovedDokument = hovedDokument,
            dokumenter = dokumenter,
            metadata = metadata
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
            "innsendingId" to innsendingId.toString(),
            "søknad_uuid" to søknadId.toString(),
            "ident" to ident
        )
    )

    fun addObserver(innsendingObserver: InnsendingObserver) {
        observers.add(innsendingObserver)
    }

    enum class TilstandType {
        Opprettet,
        AvventerMetadata,
        AvventerArkiverbarSøknad,
        AvventerMidlertidligJournalføring,
        AvventerJournalføring,
        Journalført
    }

    data class Dokument(
        val uuid: UUID = UUID.randomUUID(),
        val kravId: String?,
        val skjemakode: String?,
        val varianter: List<Dokumentvariant>
    ) {
        fun toMap() = mutableMapOf<String, Any>(
            "uuid" to uuid.toString(),
            "varianter" to varianter.map { it.toMap() }
        ).also { map ->
            skjemakode?.let { map["skjemakode"] = it }
            kravId?.let { map["kravId"] = kravId }
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

            fun toMap() = mapOf(
                "uuid" to uuid.toString(),
                "filnavn" to filnavn,
                "urn" to urn,
                "variant" to variant,
                "type" to type
            )
        }
    }

    data class Metadata(val skjemakode: String)

    enum class InnsendingType {
        NY_DIALOG,
        ETTERSENDING_TIL_DIALOG
    }
}
