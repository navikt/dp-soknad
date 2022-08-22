package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository.PersistentSøkerOppgave
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class SøknadCacheRepositoryTest {

    private val språkVerdi = "NO"

    @Test
    fun `Lagre søknad og hente`() {
        withMigratedDb {
            val søknadCache = SøknadCachePostgresRepository(dataSource)
            val søknadUuid = UUID.randomUUID()
            lagrePersonMedSøknad(søknadUuid)
            val søknad = PersistentSøkerOppgave(søknad(søknadUuid))
            søknadCache.lagre(søknad)

            val rehydrertSøknad = søknadCache.hent(søknadUuid)
            assertEquals(søknad.søknadUUID(), rehydrertSøknad.søknadUUID())
            assertEquals(søknad.eier(), rehydrertSøknad.eier())
            assertEquals(søknad.asFrontendformat(), rehydrertSøknad.asFrontendformat())
        }
    }

    @Test
    fun `Lagre samme søknad id flere ganger appendes på raden, men siste versjon av søknad hentes`() {
        val søknadUuid = UUID.randomUUID()
        withMigratedDb {
            lagrePersonMedSøknad(søknadUuid)
            val søknadCache = SøknadCachePostgresRepository(dataSource)
            søknadCache.lagre(PersistentSøkerOppgave(søknad(søknadUuid)))
            søknadCache.lagre(
                PersistentSøkerOppgave(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert første gang"
                    )
                )
            )
            søknadCache.lagre(
                PersistentSøkerOppgave(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert andre gang"
                    )
                )
            )

            val rehydrertSøknad = søknadCache.hent(søknadUuid)

            assertEquals(søknadUuid, rehydrertSøknad.søknadUUID())
            assertEquals("12345678910", rehydrertSøknad.eier())
            assertEquals(
                "oppdatert andre gang",
                rehydrertSøknad.asFrontendformat()[SøkerOppgave.Keys.SEKSJONER].asText()
            )
        }
    }

    @Test
    fun `Henter en søknad som ikke finnes`() {
        withMigratedDb {
            val søknadCache = SøknadCachePostgresRepository(dataSource)
            assertThrows<NotFoundException> { søknadCache.hent(UUID.randomUUID()) }
        }
    }

    @Test
    fun `Kan slette cache`() {
        withMigratedDb {
            val søknadCache = SøknadCachePostgresRepository(dataSource)
            val søknadUuid1 = UUID.randomUUID()
            val søknadUuid2 = UUID.randomUUID()
            val eier1 = "12345678901"
            val eier2 = "12345678902"
            lagrePersonMedSøknad(søknadUuid1, eier1)
            lagrePersonMedSøknad(søknadUuid2, eier2)
            val søknad1 = PersistentSøkerOppgave(søknad(søknadUuid1, fødselsnummer = eier1))
            val søknad2 = PersistentSøkerOppgave(søknad(søknadUuid2, fødselsnummer = eier2))

            søknadCache.slettSøknadData(søknadUuid1)

            søknadCache.lagre(søknad1)
            søknadCache.lagre(søknad2)

            assertNotNull(hentSøknadData(søknadUuid1))
            søknadCache.slettSøknadData(søknadUuid1)
            assertNull(hentSøknadData(søknadUuid1))
        }
    }

    @Test
    fun `Kan oppdatere faktum sist endret`() = withMigratedDb {
        val søknadCache = SøknadCachePostgresRepository(dataSource)
        val søknadUuid = UUID.randomUUID()
        val eier = "12345678901"
        lagrePersonMedSøknad(søknadUuid, eier)

        assertThrows<NotFoundException> { søknadCache.settFaktumSistEndret(søknadUuid) }
        assertThrows<NotFoundException> { søknadCache.hentFaktumSistEndret(søknadUuid) }

        val søknad = PersistentSøkerOppgave(søknad(søknadUuid, fødselsnummer = eier))
        søknadCache.lagre(søknad)
        val faktumFørstEndret = søknadCache.hentFaktumSistEndret(søknadUuid)
        søknadCache.settFaktumSistEndret(søknadUuid)
        val faktumSistEndret = søknadCache.hentFaktumSistEndret(søknadUuid)

        assertTrue(faktumFørstEndret < faktumSistEndret)
    }

    private fun hentSøknadData(søknadUuid: UUID) =
        using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf("SELECT soknad_data FROM soknad_cache WHERE uuid = ?", søknadUuid.toString())
                    .map { row ->
                        row.stringOrNull("soknad_data")
                    }.asSingle
            )
        }

    private fun søknad(søknadUuid: UUID, seksjoner: String = "seksjoner", fødselsnummer: String = "12345678910") =
        objectMapper.readTree(
            """{
          "@event_name": "søker_oppgave",
          "fødselsnummer": $fødselsnummer,
          "versjon_id": 0,
          "versjon_navn": "test",
          "@opprettet": "2022-05-13T14:48:09.059643",
          "@id": "76be48d5-bb43-45cf-8d08-98206d0b9bd1",
          "søknad_uuid": "$søknadUuid",
          "ferdig": false,
          "seksjoner": "$seksjoner"}"""
        )

    private fun lagrePersonMedSøknad(søknadUuid: UUID, ident: String = "01234567891") {
        val person = Person(ident)
        person.håndter(ØnskeOmNySøknadHendelse(søknadUuid, ident, språkVerdi))
        val livssyklusPostgresRepository = LivssyklusPostgresRepository(dataSource)
        livssyklusPostgresRepository.lagre(person)
    }
}
