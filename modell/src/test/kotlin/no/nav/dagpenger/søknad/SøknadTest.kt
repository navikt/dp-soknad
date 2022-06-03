package no.nav.dagpenger.søknad

import no.nav.dagpenger.søknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.søknad.Aktivitetslogg.AktivitetException
import no.nav.dagpenger.søknad.Søknad.Dokument.Variant
import no.nav.dagpenger.søknad.Søknad.Tilstand.Type.AvventerArkiverbarSøknad
import no.nav.dagpenger.søknad.Søknad.Tilstand.Type.AvventerJournalføring
import no.nav.dagpenger.søknad.Søknad.Tilstand.Type.AvventerMidlertidligJournalføring
import no.nav.dagpenger.søknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.søknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.søknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.søknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.søknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.søknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.søknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

private const val testIdent = "12345678912"
private const val testJournalpostId = "J123"

internal class SøknadTest {

    private lateinit var person: Person
    private lateinit var observatør: TestPersonObserver
    private lateinit var plantUmlObservatør: PlantUmlObservatør
    private val inspektør get() = TestSøknadInspektør(person)

    @BeforeEach
    internal fun setUp() {
        person = Person(testIdent)
        observatør = TestPersonObserver().also { person.addObserver(it) }
        plantUmlObservatør = PlantUmlObservatør().also {
            person.addObserver(it)
        }
    }

    @Test
    fun `Kan ikke oppdatere faktum når søknaden er sendt inn`() {
        håndterØnskeOmNySøknadHendelse()
        håndterNySøknadOpprettet()
        håndterSendInnSøknad()
        håndterArkiverbarSøknad()

        assertThrows<AktivitetException> {
            håndterFaktumOppdatering()
        }
    }

    @Test
    fun `Søker oppretter søknad og ferdigstiller den`() {
        håndterØnskeOmNySøknadHendelse()
        assertBehov(Behovtype.NySøknad, mapOf("ident" to testIdent, "søknad_uuid" to inspektør.søknadId.toString()))
        håndterNySøknadOpprettet()
        håndterFaktumOppdatering()
        val innsendtHendelse = håndterSendInnSøknad()
        assertBehov(
            Behovtype.ArkiverbarSøknad,
            mapOf(
                "ident" to testIdent,
                "søknad_uuid" to inspektør.søknadId.toString(),
                "innsendtTidspunkt" to innsendtHendelse.innsendtidspunkt().toString()
            )
        )
        håndterArkiverbarSøknad()

        val dokumenter = listOf(
            Søknad.Dokument(
                varianter = listOf(
                    Variant(
                        urn = "urn:dokument:1",
                        format = "ARKIV",
                        type = "PDF"
                    )
                )
            )
        )

        assertBehov(
            Behovtype.NyJournalpost,
            mapOf("dokumenter" to dokumenter, "ident" to testIdent, "søknad_uuid" to inspektør.søknadId.toString())
        )
        håndterMidlertidigJournalførtSøknad()
        håndterJournalførtSøknad()

        assertTilstander(
            UnderOpprettelse,
            Påbegynt,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført
        )

        assertPuml("Søker oppretter søknad og ferdigstiller den")
    }

    private fun håndterFaktumOppdatering() {
        person.håndter(FaktumOppdatertHendelse(inspektør.søknadId, testIdent))
    }

    @Test
    fun `oppretter en person `() {
        val person = Person(testIdent)
        object : PersonVisitor {
            init {
                person.accept(this)
            }

            override fun visitPerson(ident: String) {
                assertEquals(testIdent, ident)
            }
        }
    }

    @Test
    fun `en person kan kun ha én opprettet søknad av gangen`() {
        val person = Person(testIdent)
        person.håndter(ØnskeOmNySøknadHendelse(testIdent, UUID.randomUUID()))
        assertThrows<AktivitetException> {
            person.håndter(
                ØnskeOmNySøknadHendelse(
                    testIdent,
                    UUID.randomUUID()
                )
            )
        }
    }

    private fun assertTilstander(vararg tilstander: Søknad.Tilstand.Type) {
        assertEquals(tilstander.asList(), observatør.tilstander)
    }

    private fun håndterNySøknadOpprettet() {
        person.håndter(SøknadOpprettetHendelse(inspektør.søknadId, testIdent))
    }

    private fun håndterArkiverbarSøknad() {
        person.håndter(
            ArkiverbarSøknadMottattHendelse(
                inspektør.søknadId,
                testIdent,
                "urn:dokument:1".lagTestDokument()
            )
        )
    }

    private fun håndterMidlertidigJournalførtSøknad() {
        person.håndter(SøknadMidlertidigJournalførtHendelse(inspektør.søknadId, testIdent, testJournalpostId))
    }

    private fun håndterJournalførtSøknad() {
        person.håndter(JournalførtHendelse(testJournalpostId, testIdent))
    }

    private fun assertBehov(behovtype: Behovtype, forventetDetaljer: Map<String, Any> = emptyMap()) {
        val behov = inspektør.personLogg.behov().find {
            it.type == behovtype
        } ?: throw AssertionError("Fant ikke behov $behovtype")

        assertEquals(forventetDetaljer, behov.detaljer() + behov.kontekst())
    }

    private fun håndterSendInnSøknad(): SøknadInnsendtHendelse {
        return SøknadInnsendtHendelse(inspektør.søknadId, testIdent).also {
            person.håndter(it)
        }
    }

    private fun håndterØnskeOmNySøknadHendelse() {
        person.håndter(ØnskeOmNySøknadHendelse(testIdent, UUID.randomUUID()))
    }

    private fun assertPuml(tittel: String) {
        plantUmlObservatør.verify(tittel)
    }
}

private fun String.lagTestDokument(): Søknad.Dokument = Søknad.Dokument(
    varianter = listOf(
        Variant(this, "ARKIV", "PDF")
    )
)
