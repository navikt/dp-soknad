package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.innsending.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingPåminnelseHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingHendelse
import java.time.LocalDateTime
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
    internal var metadata: Metadata?,
) : Aktivitetskontekst {
    private val innsendinger get() = (listOf(this))
    private val observers = mutableListOf<InnsendingObserver>()

    internal constructor(
        type: InnsendingType,
        ident: String,
        søknadId: UUID,
        innsendt: ZonedDateTime,
        dokumentkrav: List<Dokument>,
        metadata: Metadata? = null,
    ) : this(
        innsendingId = UUID.randomUUID(),
        ident = ident,
        søknadId = søknadId,
        type = type,
        innsendt = innsendt,
        journalpostId = null,
        tilstand = Opprettet(),
        dokumenter = dokumentkrav,
        metadata = metadata,
    )

    companion object {
        fun ny(
            innsendt: ZonedDateTime,
            ident: String,
            søknadId: UUID,
            dokumentkrav: List<Dokument>,
        ) = Innsending(InnsendingType.NY_DIALOG, ident, søknadId, innsendt, dokumentkrav)

        fun ettersending(
            innsendt: ZonedDateTime,
            ident: String,
            søknadId: UUID,
            dokumentkrav: List<Dokument>,
        ) = Innsending(InnsendingType.ETTERSENDING_TIL_DIALOG, ident, søknadId, innsendt, dokumentkrav)

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
            metadata: Metadata?,
            sistEndretTilstand: LocalDateTime,
        ): Innsending {
            val tilstand: Tilstand =
                Innsending.Tilstand.fraType(
                    type = tilstandsType,
                    opprettet = sistEndretTilstand,
                )
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
                metadata = metadata,
            )
        }
    }

    fun hentEier() = ident

    fun håndter(hendelse: NyInnsendingHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    fun håndter(hendelse: InnsendingMetadataMottattHendelse) {
        innsendinger.forEach { it.håndterInnsendingMetadataMottatt(hendelse) }
    }

    fun håndter(hendelse: ArkiverbarSøknadMottattHendelse) {
        kontekst(hendelse)
        hendelse.info("Arkiverbar søknad mottatt")
        if (!hendelse.valider()) {
            hendelse.warn("Ikke gyldig dokumentlokasjon")
            return
        }
        innsendinger.forEach { it.håndterArkiverbarSøknadMottatt(hendelse) }
    }

    fun håndter(hendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(hendelse)
        hendelse.info("Søknad midlertidig journalført")
        innsendinger.forEach { it.håndterSøknadMidlertidigJournalført(hendelse) }
    }

    fun håndter(hendelse: JournalførtHendelse) {
        kontekst(hendelse)
        hendelse.info("Søknad journalført")
        innsendinger.forEach { it.håndterJournalført(hendelse) }
    }

    fun håndter(hendelse: InnsendingPåminnelseHendelse) {
        kontekst(hendelse)
        hendelse.info("Påminnelse om innsending")
        innsendinger.forEach { it.håndterInnsendingPåminnelse(hendelse) }
    }

    private fun håndterInnsendingMetadataMottatt(hendelse: InnsendingMetadataMottattHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun håndterArkiverbarSøknadMottatt(hendelse: ArkiverbarSøknadMottattHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun håndterSøknadMidlertidigJournalført(hendelse: SøknadMidlertidigJournalførtHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun håndterJournalført(hendelse: JournalførtHendelse) {
        if (hendelse.journalpostId() != this.journalpostId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun håndterInnsendingPåminnelse(hendelse: InnsendingPåminnelseHendelse) {
        if (hendelse.innsendingId != this.innsendingId) return
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    private fun endreTilstand(
        nyTilstand: Tilstand,
        søknadHendelse: Hendelse,
    ) {
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
                    ident = ident,
                    innsendingId = innsendingId,
                    innsendingType = type,
                    gjeldendeTilstand = tilstand.tilstandType,
                    forrigeTilstand = forrigeTilstand.tilstandType,
                    forventetFerdig = tilstand.forventetFerdig,
                ),
            )
        }
    }

    private sealed interface Tilstand : Aktivitetskontekst {
        val tilstandType: TilstandType
        val opprettet: LocalDateTime
        val forventetFerdig: LocalDateTime get() = LocalDateTime.MAX

        companion object {
            fun fraType(
                type: TilstandType,
                opprettet: LocalDateTime,
            ) = when (type) {
                TilstandType.Opprettet -> Opprettet(opprettet)
                TilstandType.AvventerMetadata -> AvventerMetadata(opprettet)
                TilstandType.AvventerArkiverbarSøknad -> AvventerArkiverbarSøknad(opprettet)
                TilstandType.AvventerMidlertidligJournalføring -> AvventerMidlertidligJournalføring(opprettet)
                TilstandType.AvventerJournalføring -> AvventerJournalføring(opprettet)
                TilstandType.Journalført -> Journalført(opprettet)
            }
        }

        fun entering(
            hendelse: Hendelse,
            innsending: Innsending,
        ) {}

        fun håndter(
            hendelse: NyInnsendingHendelse,
            innsending: Innsending,
        ) = hendelse.kanIkkeHåndteresIDenneTilstanden()

        fun håndter(
            hendelse: InnsendingMetadataMottattHendelse,
            innsending: Innsending,
        ) = hendelse.kanIkkeHåndteresIDenneTilstanden()

        fun håndter(
            hendelse: ArkiverbarSøknadMottattHendelse,
            innsending: Innsending,
        ) = hendelse.kanIkkeHåndteresIDenneTilstanden()

        fun håndter(
            hendelse: SøknadMidlertidigJournalførtHendelse,
            innsending: Innsending,
        ) = hendelse.kanIkkeHåndteresIDenneTilstanden()

        fun håndter(
            hendelse: JournalførtHendelse,
            innsending: Innsending,
        ) = hendelse.kanIkkeHåndteresIDenneTilstanden()

        fun håndter(
            hendelse: InnsendingPåminnelseHendelse,
            innsending: Innsending,
        ) {
            hendelse.kanIkkeHåndteresIDenneTilstanden()
        }

        private fun Hendelse.kanIkkeHåndteresIDenneTilstanden() =
            this.warn("Kan ikke håndtere ${this.javaClass.simpleName} i tilstand $tilstandType")

        override fun toSpesifikkKontekst() =
            this.javaClass.canonicalName.split('.').last().let {
                SpesifikkKontekst(it, emptyMap())
            }
    }

    private data class Opprettet(
        override val opprettet: LocalDateTime = LocalDateTime.now(),
    ) : Tilstand {
        override val tilstandType = TilstandType.Opprettet

        override fun håndter(
            hendelse: NyInnsendingHendelse,
            innsending: Innsending,
        ) {
            hendelse.info("Innsending ${innsending.toSpesifikkKontekst()} med id ${innsending.innsendingId} opprettet.")
            innsending.endreTilstand(AvventerMetadata(), hendelse)
        }
    }

    private data class AvventerMetadata(
        override val opprettet: LocalDateTime = LocalDateTime.now(),
    ) : Tilstand {
        override val tilstandType = TilstandType.AvventerMetadata
        override val forventetFerdig: LocalDateTime
            get() = opprettet.plusHours(1)

        override fun entering(
            hendelse: Hendelse,
            innsending: Innsending,
        ) {
            if (innsending.metadata != null) return innsending.endreTilstand(AvventerArkiverbarSøknad(), hendelse)
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingMetadata,
                "Trenger metadata/klassifisering av innsending",
            )
        }

        override fun håndter(
            hendelse: InnsendingMetadataMottattHendelse,
            innsending: Innsending,
        ) {
            innsending.metadata = hendelse.metadata
            innsending.endreTilstand(AvventerArkiverbarSøknad(), hendelse)
        }
    }

    private data class AvventerArkiverbarSøknad(
        override val opprettet: LocalDateTime = LocalDateTime.now(),
    ) : Tilstand {
        override val tilstandType = TilstandType.AvventerArkiverbarSøknad

        override val forventetFerdig: LocalDateTime
            get() = opprettet.plusHours(1)

        override fun entering(
            hendelse: Hendelse,
            innsending: Innsending,
        ) {
            val metadata = requireNotNull(innsending.metadata) { "Må ha metadata" }
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad,
                "Trenger søknad på et arkiverbart format",
                mapOf(
                    "innsendtTidspunkt" to innsending.innsendt.toString(),
                    "dokumentasjonKravId" to innsending.dokumenter.map { it.kravId },
                    "skjemakode" to metadata.skjemakode,
                ),
            )
        }

        override fun håndter(
            hendelse: ArkiverbarSøknadMottattHendelse,
            innsending: Innsending,
        ) {
            val metadata = requireNotNull(innsending.metadata) { "Må ha metadata" }
            innsending.hovedDokument =
                Dokument(
                    kravId = null,
                    skjemakode = metadata.skjemakode,
                    varianter = hendelse.dokumentvarianter(),
                )
            innsending.endreTilstand(
                AvventerMidlertidligJournalføring(),
                hendelse,
            )
        }

        override fun håndter(
            hendelse: InnsendingPåminnelseHendelse,
            innsending: Innsending,
        ) {
            this.entering(hendelse, innsending)
        }
    }

    private data class AvventerMidlertidligJournalføring(
        override val opprettet: LocalDateTime = LocalDateTime.now(),
    ) : Tilstand {
        override val tilstandType = TilstandType.AvventerMidlertidligJournalføring
        override val forventetFerdig: LocalDateTime
            get() = opprettet.plusHours(1)

        override fun entering(
            hendelse: Hendelse,
            innsending: Innsending,
        ) {
            val hovedDokument = requireNotNull(innsending.hovedDokument) { "Hoveddokument må være satt" }
            val dokumenter = innsending.dokumenter
            hendelse.info("Lager journalpost med dokumenter=${dokumenter.size}")
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost,
                "Trenger å journalføre søknad",
                mapOf(
                    // urn til netto/brutto
                    "hovedDokument" to hovedDokument.toMap(),
                    "dokumenter" to dokumenter.map { it.toMap() },
                ),
            )
        }

        override fun håndter(
            hendelse: SøknadMidlertidigJournalførtHendelse,
            innsending: Innsending,
        ) {
            innsending.journalpostId = hendelse.journalpostId()
            innsending.endreTilstand(AvventerJournalføring(), hendelse)
        }

        override fun håndter(
            hendelse: InnsendingPåminnelseHendelse,
            innsending: Innsending,
        ) {
            this.entering(hendelse, innsending)
        }
    }

    private data class AvventerJournalføring(
        override val opprettet: LocalDateTime = LocalDateTime.now(),
    ) : Tilstand {
        override val tilstandType = TilstandType.AvventerJournalføring
        override val forventetFerdig: LocalDateTime
            get() = opprettet.plusHours(1)

        override fun håndter(
            hendelse: JournalførtHendelse,
            innsending: Innsending,
        ) {
            if (hendelse.journalpostId() == innsending.journalpostId) {
                innsending.endreTilstand(Journalført(), hendelse)
            }
        }
    }

    private data class Journalført(
        override val opprettet: LocalDateTime = LocalDateTime.now(),
    ) : Tilstand {
        override val tilstandType = TilstandType.Journalført
    }

    fun accept(visitor: InnsendingVisitor) {
        visitor.visit(
            innsendingId = innsendingId,
            søknadId = søknadId,
            ident = ident,
            innsendingType = type,
            tilstand = tilstand.tilstandType,
            sistEndretTilstand = tilstand.opprettet,
            innsendt = innsendt,
            journalpost = journalpostId,
            hovedDokument = hovedDokument,
            dokumenter = dokumenter,
            metadata = metadata,
        )
    }

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

    override fun toSpesifikkKontekst() =
        SpesifikkKontekst(
            kontekstType = "innsending",
            mapOf(
                "type" to type.name,
                "innsendingId" to innsendingId.toString(),
                "søknad_uuid" to søknadId.toString(),
                "ident" to ident,
            ),
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
        Journalført,
    }

    data class Dokument(
        val uuid: UUID = UUID.randomUUID(),
        val kravId: String?,
        val skjemakode: String?,
        val varianter: List<Dokumentvariant>,
    ) {
        fun toMap() =
            mutableMapOf<String, Any>(
                "uuid" to uuid.toString(),
                "varianter" to varianter.map { it.toMap() },
            ).also { map ->
                skjemakode?.let { map["skjemakode"] = it }
                kravId?.let { map["kravId"] = kravId }
            }

        data class Dokumentvariant(
            val uuid: UUID = UUID.randomUUID(),
            val filnavn: String,
            val urn: String,
            val variant: String,
            val type: String,
        ) {
            init {
                kotlin
                    .runCatching {
                        URN.rfc8141().parse(urn)
                    }.onFailure {
                        throw IllegalArgumentException("Ikke gyldig URN: $urn")
                    }
            }

            fun toMap() =
                mapOf(
                    "uuid" to uuid.toString(),
                    "filnavn" to filnavn,
                    "urn" to urn,
                    "variant" to variant,
                    "type" to type,
                )
        }
    }

    data class Metadata(
        val skjemakode: String,
    )

    enum class InnsendingType {
        NY_DIALOG,
        ETTERSENDING_TIL_DIALOG,
    }
}
