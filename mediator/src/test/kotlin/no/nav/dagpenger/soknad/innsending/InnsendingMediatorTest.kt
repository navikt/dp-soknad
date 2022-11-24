package no.nav.dagpenger.soknad.innsending

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.InnsendingType.NY_DIALOG
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerArkiverbarSøknad
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerJournalføring
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerMetadata
import no.nav.dagpenger.soknad.Innsending.TilstandType.AvventerMidlertidligJournalføring
import no.nav.dagpenger.soknad.Innsending.TilstandType.Journalført
import no.nav.dagpenger.soknad.InnsendingObserver
import no.nav.dagpenger.soknad.hendelse.innsending.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingHendelse
import no.nav.dagpenger.soknad.utils.asZonedDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

internal class InnsendingMediatorTest {
    private val rapid = TestRapid()
    private val repository = InMemoryInnsendingRepository()
    private val mediator = InnsendingMediator(rapid, repository)
    private val ident = "123123123"
    private val innsendingObserver = TestInnsendingObserver()

    @Test
    fun `Oppretter innsending`() {
        val innsending = Innsending.ny(ZonedDateTime.now(), ident, listOf())
        innsending.addObserver(innsendingObserver)

        mediator.behandle(NyInnsendingHendelse(innsending, ident))

        assertEquals(AvventerMetadata, innsendingObserver.gjeldendeTilstand)

        with(rapid.inspektør.message(0)) {
            assertEquals(listOf(Behovtype.InnsendingMetadata.name), this["@behov"].map { it.asText() })
            assertEquals(NY_DIALOG.name, this["type"].asText())
            assertEquals(ident, this["ident"].asText())
            assertTrue(this.has("søknad_uuid"))
        }
    }

    @Test
    fun `Håndterer metadata`() {
        val innsendt = ZonedDateTime.now()
        val skjemaKode = "04.04-04"
        val innsending = Innsending.ny(innsendt, ident, listOf())
        innsending.addObserver(innsendingObserver)

        mediator.behandle(NyInnsendingHendelse(innsending, ident))
        mediator.behandle(
            InnsendingMetadataMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                skjemaKode = skjemaKode
            )
        )

        assertEquals(AvventerArkiverbarSøknad, innsendingObserver.gjeldendeTilstand)

        with(rapid.inspektør.message(1)) {
            assertEquals(listOf(Behovtype.ArkiverbarSøknad.name), this["@behov"].map { it.asText() })
            assertEquals(NY_DIALOG.name, this["type"].asText())
            assertEquals(innsendt, this["innsendtTidspunkt"].asZonedDateTime())
            assertEquals(listOf<String>(), this["dokumentasjonKravId"].map { it.asText() })
            assertEquals(skjemaKode, this["skjemakode"].asText())
        }
    }

    @Test
    fun `Håndterer ArkiverbarSøknadHendelse`() {
        val innsendt = ZonedDateTime.now()
        val skjemaKode = "04.04-04"
        val innsending = Innsending.ny(innsendt, ident, listOf())
        innsending.addObserver(innsendingObserver)

        mediator.behandle(NyInnsendingHendelse(innsending, ident))
        mediator.behandle(
            InnsendingMetadataMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                skjemaKode = skjemaKode
            )
        )
        mediator.behandle(
            ArkiverbarSøknadMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                dokumentvarianter = listOf()
            )
        )

        assertEquals(AvventerMidlertidligJournalføring, innsendingObserver.gjeldendeTilstand)

        with(rapid.inspektør.message(2)) {
            assertEquals(listOf(Behovtype.NyJournalpost.name), this["@behov"].map { it.asText() })
            assertEquals(NY_DIALOG.name, this["type"].asText())
            // TODO; Lag dokumenter på innsendinger
            assertTrue(this.has("hovedDokument"))
            assertTrue(this.has("dokumenter"))
        }
    }

    @Test
    fun `Håndterer midlertidlig journalføring`() {
        val innsendt = ZonedDateTime.now()
        val skjemaKode = "04.04-04"
        val journalpostId = "123"
        val innsending = Innsending.ny(innsendt, ident, listOf())
        innsending.addObserver(innsendingObserver)

        mediator.behandle(NyInnsendingHendelse(innsending, ident))
        mediator.behandle(
            InnsendingMetadataMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                skjemaKode = skjemaKode
            )
        )
        mediator.behandle(
            ArkiverbarSøknadMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                dokumentvarianter = listOf()
            )
        )
        mediator.behandle(
            SøknadMidlertidigJournalførtHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                journalpostId = journalpostId
            )
        )

        assertEquals(AvventerJournalføring, innsendingObserver.gjeldendeTilstand)
    }

    @Test
    fun `Håndterer ferdigstilt journalføring`() {
        val innsendt = ZonedDateTime.now()
        val skjemaKode = "04.04-04"
        val journalpostId = "123"
        val innsending = Innsending.ny(innsendt, ident, listOf())
        innsending.addObserver(innsendingObserver)

        mediator.behandle(NyInnsendingHendelse(innsending, ident))
        mediator.behandle(
            InnsendingMetadataMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                skjemaKode = skjemaKode
            )
        )
        mediator.behandle(
            ArkiverbarSøknadMottattHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                dokumentvarianter = listOf()
            )
        )
        mediator.behandle(
            SøknadMidlertidigJournalførtHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                journalpostId = journalpostId
            )
        )
        mediator.behandle(
            JournalførtHendelse(
                innsendingId = innsending.innsendingId,
                ident = ident,
                journalpostId = journalpostId
            )
        )

        assertEquals(Journalført, innsendingObserver.gjeldendeTilstand)
    }
}

class TestInnsendingObserver : InnsendingObserver {
    lateinit var gjeldendeTilstand: Innsending.TilstandType

    override fun innsendingTilstandEndret(event: InnsendingObserver.InnsendingEndretTilstandEvent) {
        gjeldendeTilstand = event.gjeldendeTilstand
    }
}

private class InMemoryInnsendingRepository : InnsendingRepository {
    private val innsendinger = mutableMapOf<UUID, Innsending>()

    override fun hent(innsendingId: UUID) = innsendinger[innsendingId]

    override fun lagre(innsending: Innsending) {
        innsendinger[innsending.innsendingId] = innsending
    }

    override fun finnFor(søknadsId: UUID): List<Innsending> {
        TODO("Not yet implemented")
    }
}
