package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.db.SøkerOppgaveNotFoundException
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class SøknadDataRepositoryTest {
    @Test
    fun `Lagre søknad og hente`() {
        withMigratedDb {
            val søknadCache = SøknadDataPostgresRepository(dataSource)
            val søknadUuid = UUID.randomUUID()
            lagrePersonMedSøknad(søknadUuid)
            val søknad = SøkerOppgaveMelding(søknad(søknadUuid))
            søknadCache.lagre(søknad)
            val rehydrertSøknad = søknadCache.hentSøkerOppgave(søknadUuid)
            assertEquals(søknad.søknadUUID(), rehydrertSøknad.søknadUUID())
            assertEquals(søknad.eier(), rehydrertSøknad.eier())
            with(jacksonObjectMapper()) {
                assertEquals(readTree(søknad.toJson()), readTree(rehydrertSøknad.toJson()))
            }

            assertEquals(1, søknadCache.besvart(søknadUuid))
            søknadCache.lagre(søknad)
            assertEquals(2, søknadCache.besvart(søknadUuid))
            søknadCache.lagre(søknad)
            assertEquals(3, søknadCache.besvart(søknadUuid))
        }
    }

    @Test
    fun `Lagre samme søknad id flere ganger appendes på raden, men siste versjon av søknad hentes`() {
        val søknadUuid = UUID.randomUUID()
        withMigratedDb {
            lagrePersonMedSøknad(søknadUuid)
            val søknadCache = SøknadDataPostgresRepository(dataSource)
            søknadCache.lagre(SøkerOppgaveMelding(søknad(søknadUuid)))
            søknadCache.lagre(
                SøkerOppgaveMelding(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert første gang"
                    )
                )
            )
            søknadCache.lagre(
                SøkerOppgaveMelding(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert andre gang"
                    )
                )
            )
            val rehydrertSøknad = søknadCache.hentSøkerOppgave(søknadUuid)

            assertEquals(søknadUuid, rehydrertSøknad.søknadUUID())
            assertEquals("12345678910", rehydrertSøknad.eier())
            assertTrue(rehydrertSøknad.toJson().contains("oppdatert andre gang"))
        }
    }

    @Test
    fun `Henter en søknad som ikke finnes`() {
        withMigratedDb {
            val søknadCache = SøknadDataPostgresRepository(dataSource)
            assertThrows<SøkerOppgaveNotFoundException> { søknadCache.hentSøkerOppgave(UUID.randomUUID()) }
        }
    }

    @Test
    fun `Kan slette cache`() {
        withMigratedDb {
            val søknadCache = SøknadDataPostgresRepository(dataSource)
            val søknadUuid1 = UUID.randomUUID()
            val søknadUuid2 = UUID.randomUUID()
            val eier1 = "12345678901"
            val eier2 = "12345678901"
            lagrePersonMedSøknad(søknadUuid1, eier1)
            lagrePersonMedSøknad(søknadUuid2, eier2)
            val søknad1 = SøkerOppgaveMelding(søknad(søknadUuid1, fødselsnummer = eier1))
            val søknad2 = SøkerOppgaveMelding(søknad(søknadUuid2, fødselsnummer = eier2))

            søknadCache.lagre(søknad1)
            søknadCache.lagre(søknad2)
            assertAntallRader(2)

            søknadCache.slett(søknadUuid1)
            assertAntallRader(1)

            søknadCache.slett(søknadUuid2)
            assertAntallRader(0)
        }
    }

    private fun assertAntallRader(antallRader: Int) {
        val faktiskeRader = using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("select count(1) from soknad_data").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: soknad_data")
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
        val søknadRepository = SøknadPostgresRepository(dataSource)
        søknadRepository.lagre(Søknad(søknadUuid, Språk("NO"), ident))
    }
}
