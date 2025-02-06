package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import FerdigSøknadData
import io.ktor.server.plugins.NotFoundException
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

internal class FerdigstiltSøknadPostgresRepositoryTest {
    private val språkVerdi = "NO"

    @Language("JSON")
    private val dummyTekst = """{ "id": "value" }"""
    private val dummyTekst2 = """{ "id2": "value2" }"""
    private val dummyFakta = """{ "fakta1": "value1" }"""

    @Test
    fun `kan lagre og hente søknads tekst`() {
        withMigratedDb {
            val søknadId = lagRandomPersonOgSøknad()

            FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                db.lagreSøknadsTekst(søknadId, dummyTekst)
                val actualJson = db.hentTekst(søknadId)
                assertJsonEquals(dummyTekst, actualJson)
            }
        }
    }

    @Test
    fun `kaster expetion hvis søknadstekst ikke finnes`() {
        assertThrows<NotFoundException> {
            withMigratedDb {
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).hentTekst(UUID.randomUUID())
            }
        }
    }

    @Test
    fun `Skriver ikke over tekst når søknad allerede finnes`() {
        val ferdigstiltSøknadPostgresRepository =
            FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
        withMigratedDb {
            val søknadUUID = lagRandomPersonOgSøknad()
            ferdigstiltSøknadPostgresRepository.lagreSøknadsTekst(
                søknadUUID,
                dummyTekst,
            )
            ferdigstiltSøknadPostgresRepository.lagreSøknadsTekst(
                søknadUUID,
                dummyTekst2,
            )
            assertJsonEquals(dummyTekst, ferdigstiltSøknadPostgresRepository.hentTekst(søknadUUID))
        }
    }

    @Test
    fun `kan hente søknadsfakta`() {
        withMigratedDb {
            val søknadId = lagreFakta(dummyFakta)
            FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                val actualJson = db.hentFakta(søknadId)
                assertJsonEquals(dummyFakta, actualJson)
            }
        }
    }

    @Test
    fun `kaster exception dersom søknadsfakta ikke eksister`() {
        withMigratedDb {
            assertThrows<NotFoundException> {
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).hentFakta(UUID.randomUUID())
            }
        }
    }

    private fun lagreFakta(fakta: String): UUID {
        val søknadId = UUID.randomUUID()
        val ident = "01234567891"
        SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).run {
            lagre(Søknad(søknadId, Språk(språkVerdi), ident))
        }
        val søknadCachePostgresRepository = SøknadDataPostgresRepository(PostgresDataSourceBuilder.dataSource)
        søknadCachePostgresRepository.lagre(TestSøkerOppgave(søknadId, ident, fakta))
        return søknadId
    }

    private fun lagRandomPersonOgSøknad(): UUID {
        val søknadId = UUID.randomUUID()

        SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).lagre(
            Søknad.rehydrer(
                søknadId = søknadId,
                ident = "12345678910",
                opprettet = ZonedDateTime.now(),
                innsendt = null,
                språk = Språk(språkVerdi),
                dokumentkrav = Dokumentkrav(),
                sistEndretAvBruker = ZonedDateTime.now(),
                tilstandsType = Påbegynt,
                aktivitetslogg = Aktivitetslogg(),
                prosessversjon = null,
                data = FerdigSøknadData,
            ),
        )
        return søknadId
    }

    private fun assertJsonEquals(
        expected: String,
        actual: String,
    ) {
        fun String.removeWhitespace(): String = this.replace("\\s".toRegex(), "")
        assertEquals(expected.removeWhitespace(), actual.removeWhitespace())
    }
}
