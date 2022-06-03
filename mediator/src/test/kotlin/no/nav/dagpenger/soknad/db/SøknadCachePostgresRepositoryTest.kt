package no.nav.dagpenger.soknad.db

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.søknad.db.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.søknad.faktumflyt.SøkerOppgave
import no.nav.dagpenger.soknad.søknad.faktumflyt.SøknadCachePostgresRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class SøknadCachePostgresRepositoryTest {

    @Test
    fun `Lagre søknad og hente`() {
        withMigratedDb {
            val søknadCache = SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadUuid = UUID.randomUUID()
            lagrePersonMedSøknad(søknadUuid)
            val søknad = LivssyklusPostgresRepository.PersistentSøkerOppgave(søknad(søknadUuid))
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
            val søknadCache = SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource)
            søknadCache.lagre(LivssyklusPostgresRepository.PersistentSøkerOppgave(søknad(søknadUuid)))
            søknadCache.lagre(
                LivssyklusPostgresRepository.PersistentSøkerOppgave(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert første gang"
                    )
                )
            )
            søknadCache.lagre(
                LivssyklusPostgresRepository.PersistentSøkerOppgave(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert andre gang"
                    )
                )
            )

            val rehydrertSøknad = søknadCache.hent(søknadUuid)

            assertEquals(søknadUuid, rehydrertSøknad.søknadUUID())
            assertEquals("12345678910", rehydrertSøknad.eier())
            assertEquals("oppdatert andre gang", rehydrertSøknad.asFrontendformat()[SøkerOppgave.Keys.SEKSJONER].asText())
        }
    }

    @Test
    fun `Henter en søknad som ikke finnes`() {
        withMigratedDb {
            val søknadCache = SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource)
            assertThrows<NotFoundException> { søknadCache.hent(UUID.randomUUID()) }
        }
    }

    @Test
    fun `Kan slette cache`() {
        withMigratedDb {
            val søknadCache = SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadUuid1 = UUID.randomUUID()
            val søknadUuid2 = UUID.randomUUID()

            val eier1 = "12345678901"
            val eier2 = "12345678902"
            lagrePersonMedSøknad(søknadUuid1, eier1)
            lagrePersonMedSøknad(søknadUuid2, eier2)
            val søknad1 = LivssyklusPostgresRepository.PersistentSøkerOppgave(søknad(søknadUuid1, fødselsnummer = eier1))
            val søknad2 = LivssyklusPostgresRepository.PersistentSøkerOppgave(søknad(søknadUuid2, fødselsnummer = eier2))

            søknadCache.slett(søknadUuid1, eier1)
            assertAntallRader(antallRader = 0)

            søknadCache.lagre(søknad1)
            søknadCache.lagre(søknad2)

            assertAntallRader(antallRader = 2)

            søknadCache.slett(søknadUuid1, eier1)
            assertAntallRader(antallRader = 1)
        }
    }

    private fun assertAntallRader(antallRader: Int) {
        val faktiskeRader = using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("select count(1) from soknad_cache").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: soknad_cache")
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
        person.håndter(ØnskeOmNySøknadHendelse(ident, søknadUuid))
        val livssyklusPostgresRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
        livssyklusPostgresRepository.lagre(person)
    }
}
