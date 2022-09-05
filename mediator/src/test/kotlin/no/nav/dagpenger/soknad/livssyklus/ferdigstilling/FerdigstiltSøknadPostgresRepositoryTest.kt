package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import io.ktor.server.plugins.NotFoundException
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøknadCachePostgresRepository
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
    fun `kaster exeption når søknaduuid allerede finnes`() {
        assertThrows<IllegalArgumentException> {
            withMigratedDb {
                val søknadUUID = lagRandomPersonOgSøknad()
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).lagreSøknadsTekst(
                    søknadUUID,
                    dummyTekst
                )
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).lagreSøknadsTekst(
                    søknadUUID,
                    dummyTekst
                )
            }
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
        val livssyklusPostgresRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
        val søknadhåndterer = Søknadhåndterer(ident)
        søknadhåndterer.håndter(ØnskeOmNySøknadHendelse(søknadId, ident, språkVerdi))
        livssyklusPostgresRepository.lagre(søknadhåndterer, ident)
        val søknadCachePostgresRepository = SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource)
        søknadCachePostgresRepository.lagre(TestSøkerOppgave(søknadId, ident, fakta))
        return søknadId
    }

    private fun lagRandomPersonOgSøknad(): UUID {
        val søknadId = UUID.randomUUID()
        val originalSøknadhåndterer = Søknadhåndterer("12345678910") {
            mutableListOf(
                Søknad(søknadId, Språk(språkVerdi), it, "12345678910"),
                Søknad.rehydrer(
                    søknadId = søknadId,
                    søknadhåndterer = it,
                    ident = "12345678910",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk = Språk(språkVerdi),
                    dokumentkrav = Dokumentkrav(),
                    sistEndretAvBruker = ZonedDateTime.now(),
                    tilstandsType = Journalført.name
                )
            )
        }
        LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).lagre(originalSøknadhåndterer, "12345678910")
        return søknadId
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        fun String.removeWhitespace(): String = this.replace("\\s".toRegex(), "")
        assertEquals(expected.removeWhitespace(), actual.removeWhitespace())
    }
}
