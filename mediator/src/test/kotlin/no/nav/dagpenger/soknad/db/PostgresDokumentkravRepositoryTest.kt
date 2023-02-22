package no.nav.dagpenger.soknad.db

import FerdigSøknadData
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SEND_NÅ
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SEND_SENERE
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class PostgresDokumentkravRepositoryTest {
    private val now = LocalDateTime.now().atZone(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.HOURS)
    private val dokumentFaktum1 = Faktum(faktumJson(id = "1", beskrivendeId = "f1", generertAv = "foobar"))
    private val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson(id = "2", beskrivendeId = "f2")))

    private val sannsynliggjøring1 = Sannsynliggjøring(
        id = dokumentFaktum1.id,
        faktum = dokumentFaktum1,
        sannsynliggjør = faktaSomSannsynliggjøres
    )

    val søknadId = UUID.randomUUID()
    val dokumentkrav = Dokumentkrav(søknadId = søknadId).also { it.håndter(setOf(sannsynliggjøring1)) }

    private lateinit var dokumentkravRepository: PostgresDokumentkravRepository

    val fil1 = Krav.Fil(
        filnavn = "fil1",
        urn = URN.rfc8141().parse("urn:vedlegg:$søknadId/fil1"),
        storrelse = 0,
        tidspunkt = now,
        bundlet = false
    )
    val fil2 = fil1.copy(filnavn = "fil2", urn = URN.rfc8141().parse("urn:vedlegg:$søknadId/fil2"))

    @BeforeEach
    @Test
    fun setup() {
        setup(lagSøknad(søknadId, ident = "123")) {
            it.lagre(søknadId, dokumentkrav)
            dokumentkravRepository = it
        }
    }

    @Test
    fun `lagring og henting av dokumentkrav`() {
        setup(lagSøknad(søknadId, ident = "456")) {
            it.lagre(søknadId, dokumentkrav)
            assertDoesNotThrow {
                dokumentkravRepository.lagre(
                    søknadId = søknadId,
                    dokumentkrav = dokumentkrav
                )
            }

            val aktiveDokumentKrav = dokumentkravRepository.hent(søknadId).aktiveDokumentKrav()
            assertEquals(1, aktiveDokumentKrav.size)
        }
    }
    @Test
    fun `Kan legge til og slette filer`() {

        dokumentkravRepository.håndter(
            LeggTilFil(
                søknadID = søknadId,
                ident = "123",
                kravId = "1",
                fil = fil1
            )
        )

        dokumentkravRepository.håndter(
            LeggTilFil(
                søknadID = søknadId,
                ident = "123",
                kravId = "1",
                fil = fil2
            )
        )

        dokumentkravRepository.hent(søknadId).let { dokumentKrav ->
            assertEquals(1, dokumentKrav.aktiveDokumentKrav().size)
            val svar = dokumentKrav.aktiveDokumentKrav().single().svar
            assertEquals(setOf(fil1, fil2), svar.filer)
            assertEquals(SEND_NÅ, svar.valg)
        }

        dokumentkravRepository.håndter(
            SlettFil(
                søknadID = søknadId,
                ident = "123",
                kravId = "1",
                urn = fil1.urn
            )
        )

        dokumentkravRepository.hent(søknadId).let { dokumentKrav ->
            assertEquals(1, dokumentKrav.aktiveDokumentKrav().size)
            assertEquals(setOf(fil2), dokumentKrav.aktiveDokumentKrav().single().svar.filer)
        }
    }

    @Test
    fun `Skal håndtere Dokumentasjon ikke tilgjengelig`() {
        dokumentkravRepository.håndter(
            DokumentasjonIkkeTilgjengelig(
                søknadID = søknadId,
                ident = "123",
                kravId = "1",
                valg = SEND_SENERE,
                begrunnelse = "Begrunnelse"
            )
        )

        dokumentkravRepository.hent(søknadId).let { krav ->
            println(krav.aktiveDokumentKrav().first())
            val svar = krav.aktiveDokumentKrav().first().svar
            assertEquals(SEND_SENERE, svar.valg)
            assertEquals("Begrunnelse", svar.begrunnelse)
        }
    }

    @Test
    fun `Skal oppdatere dokumentkravene med bundle-info`() {
        dokumentkravRepository.håndter(
            LeggTilFil(
                søknadID = søknadId,
                ident = "123",
                kravId = "1",
                fil = fil1
            )
        )

        dokumentkravRepository.håndter(
            LeggTilFil(
                søknadID = søknadId,
                ident = "4567",
                kravId = "1",
                fil = fil2
            )
        )

        dokumentkravRepository.hent(søknadId).let { dokumentKrav ->
            assertNull(dokumentKrav.aktiveDokumentKrav().single().svar.bundle)
            assertEquals(2, dokumentKrav.aktiveDokumentKrav().single().svar.filer.size)
            assertFalse(dokumentKrav.aktiveDokumentKrav().single().svar.filer.all { it.bundlet })
        }

        val bundleUrn = URN.rfc8141().parse("urn:bundle:1")

        dokumentkravRepository.håndter(
            DokumentKravSammenstilling(
                søknadID = søknadId,
                ident = "123",
                kravId = "1",
                urn = bundleUrn
            )
        )

        dokumentkravRepository.hent(søknadId).let { dokumentKrav ->
            val svar = dokumentKrav.aktiveDokumentKrav().single().svar
            assertEquals(bundleUrn, svar.bundle)
            assertEquals(SEND_NÅ, svar.valg)
            assertTrue(svar.filer.all { it.bundlet })
        }
    }

    private fun setup(søknad: Søknad, block: (repository: PostgresDokumentkravRepository) -> Unit) {
        withMigratedDb {
            SøknadPostgresRepository(dataSource = PostgresDataSourceBuilder.dataSource).lagre(søknad)
            block(PostgresDokumentkravRepository(PostgresDataSourceBuilder.dataSource))
        }
    }

    private fun lagSøknad(
        søknadId: UUID = UUID.randomUUID(),
        ident: String = "1234",
    ): Søknad {
        return Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = now,
            innsendt = now,
            språk = Språk(verdi = "NO"),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = Prosessversjon("Dagpenger", 1),
            data = FerdigSøknadData
        )
    }
}
