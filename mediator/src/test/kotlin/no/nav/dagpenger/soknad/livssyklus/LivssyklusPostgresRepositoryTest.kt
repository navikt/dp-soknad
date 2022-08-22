package no.nav.dagpenger.soknad.livssyklus

import com.fasterxml.jackson.module.kotlin.contains
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository.PersistentSøkerOppgave
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

internal class LivssyklusPostgresRepositoryTest {
    private val språk = Språk("NO")
    @Test
    fun `Lagre og hente person uten søknader`() {
        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                val expectedPerson = Person("12345678910")
                it.lagre(expectedPerson)
                val person = it.hent("12345678910")

                assertNotNull(person)
                assertEquals(expectedPerson, person)

                assertDoesNotThrow {
                    it.lagre(expectedPerson)
                }
            }
        }
    }

    @Test
    fun `Hent person som ikke finnes`() {
        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                assertNull(it.hent("finnes ikke"))
            }
        }
    }

    @Test
    fun `Lagre og hente person med søknader, dokumenter og aktivitetslogg`() {
        val søknadId1 = UUID.randomUUID()
        val søknadId2 = UUID.randomUUID()
        val originalPerson = Person("12345678910") {
            mutableListOf(
                Søknad(søknadId1, språk, it),
                Søknad.rehydrer(
                    søknadId = søknadId2,
                    person = it,
                    tilstandsType = Journalført.name,
                    dokument = Søknad.Dokument(
                        varianter = listOf(
                            Søknad.Dokument.Variant(
                                urn = "urn:soknad:fil1",
                                format = "ARKIV",
                                type = "PDF"
                            ),
                            Søknad.Dokument.Variant(
                                urn = "urn:soknad:fil2",
                                format = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk
                )
            )
        }

        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(originalPerson)
                val personFraDatabase = it.hent("12345678910", true)
                assertNotNull(personFraDatabase)
                assertEquals(originalPerson, personFraDatabase)

                val fraDatabaseVisitor = TestPersonVisitor(personFraDatabase)
                val originalVisitor = TestPersonVisitor(originalPerson)
                val søknaderFraDatabase = fraDatabaseVisitor.søknader
                val originalSøknader = originalVisitor.søknader
                assertNull(fraDatabaseVisitor.dokumenter[søknadId1])
                assertEquals(2, fraDatabaseVisitor.dokumenter[søknadId2]?.varianter?.size)
                assertDeepEquals(originalSøknader.first(), søknaderFraDatabase.first())
                assertDeepEquals(originalSøknader.last(), søknaderFraDatabase.last())

                assertAntallRader("aktivitetslogg_v2", 1)
                assertEquals(originalVisitor.aktivitetslogg.toString(), fraDatabaseVisitor.aktivitetslogg.toString())
            }
        }
    }

    @Test
    fun `Henter påbegynte søknader`() {
        val påbegyntSøknadUuid = UUID.randomUUID()
        val innsendtTidspunkt = ZonedDateTime.now()
        val person = Person("12345678910") {
            mutableListOf(
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadUuid,
                    person = it,
                    tilstandsType = Påbegynt.name,
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "jouhasjk",
                    innsendtTidspunkt = innsendtTidspunkt,
                    språk
                ),
                Søknad.rehydrer(
                    søknadId = UUID.randomUUID(),
                    person = it,
                    tilstandsType = Journalført.name,
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = innsendtTidspunkt,
                    språk
                )
            )
        }

        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(person)
                val påbegyntSøknad = it.hentPåbegyntSøknad(person.ident())!!
                assertEquals(påbegyntSøknadUuid, påbegyntSøknad.uuid)
                assertEquals(LocalDate.from(innsendtTidspunkt), påbegyntSøknad.startDato)

                assertEquals(null, it.hentPåbegyntSøknad("hubbba"))
            }
        }
    }

    @Test
    fun `Kan oppdatere søknader`() {
        val søknadId = UUID.randomUUID()
        val originalPerson = Person("12345678910") {
            mutableListOf(
                Søknad(søknadId, språk, it),
                Søknad.rehydrer(
                    søknadId = søknadId,
                    person = it,
                    tilstandsType = Journalført.name,
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk
                )
            )
        }

        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(originalPerson)
                val personFraDatabase = it.hent("12345678910")
                assertNotNull(personFraDatabase)

                val søknaderFraDatabase = TestPersonVisitor(personFraDatabase).søknader
                assertEquals(1, søknaderFraDatabase.size)
            }
        }
    }

    @Test
    fun `Fødselsnummer skal ikke komme med som en del av frontendformatet, men skal fortsatt være en del av søknaden`() {
        val søknadJson = søknad(UUID.randomUUID())
        val søknad = PersistentSøkerOppgave(søknadJson)

        val frontendformat = søknad.asFrontendformat()
        assertFalse(frontendformat.contains(SøkerOppgave.Keys.FØDSELSNUMMER))
        assertNotNull(søknad.eier())
    }

    private fun assertDeepEquals(expected: Søknad, result: Søknad) {
        assertTrue(expected.deepEquals(result), "Søknadene var ikke like")
    }

    private class TestPersonVisitor(person: Person?) : PersonVisitor {
        val dokumenter: MutableMap<UUID, Søknad.Dokument> = mutableMapOf()
        lateinit var søknader: List<Søknad>

        lateinit var aktivitetslogg: Aktivitetslogg

        init {
            person?.accept(this)
        }

        override fun visitPerson(ident: String, søknader: List<Søknad>) {
            this.søknader = søknader
        }

        override fun visitSøknad(
            søknadId: UUID,
            person: Person,
            tilstand: Søknad.Tilstand,
            dokument: Søknad.Dokument?,
            journalpostId: String?,
            innsendtTidspunkt: ZonedDateTime?,
            språk: Språk
        ) {
            dokument?.let { dokumenter[søknadId] = it }
        }

        override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
            this.aktivitetslogg = aktivitetslogg
        }
    }

    private fun assertAntallRader(tabell: String, antallRader: Int) {
        val faktiskeRader = using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("select count(1) from $tabell").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: $tabell")
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
  "seksjoner": "$seksjoner"
}"""
        )
}
