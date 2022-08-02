package no.nav.dagpenger.soknad.sletterutine

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøknadCachePostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

internal class VaktmesterRepositoryTest {
    private val språk = Språk("NO")
    private val gammelPåbegyntSøknadUuid = UUID.randomUUID()
    private val journalførtSøknadUuid = UUID.randomUUID()
    private val testPersonIdent = "12345678910"
    private val dagerFørPåbegynteSøknaderSlettes = 7L

    @Test
    fun `Sletter søknader og søknadcache med tilstand påbegynt etter gitt tidsinterval`() = withMigratedDb {
        val livssyklusRepository = LivssyklusPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource)
        val person = Person(testPersonIdent) {
            mutableListOf(påbegyntGammelSøknad(gammelPåbegyntSøknadUuid, it), journalførtSøknad(journalførtSøknadUuid, it))
        }
        livssyklusRepository.lagre(person)
        søknadCacheRepository.lagre(TestSøkerOppgave(gammelPåbegyntSøknadUuid, testPersonIdent, "{}"))

        val nå = LocalDateTime.now()
        endreSøknadOpprettet(gammelPåbegyntSøknadUuid, nå.minusDays(30), dataSource)
        endreSøknadOpprettet(journalførtSøknadUuid, nå.minusDays(30), dataSource)

        val harSlettetRad = vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(nå.minusDays(dagerFørPåbegynteSøknaderSlettes))
        assertEquals(1, harSlettetRad)
        assertThrows<NotFoundException> { søknadCacheRepository.hent(gammelPåbegyntSøknadUuid) }

        livssyklusRepository.hent(person.ident()).also { oppdatertPerson ->
            assertEquals(1, TestPersonVisitor(oppdatertPerson).søknader.size)
        }
    }

    private fun journalførtSøknad(journalførtSøknadId: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = journalførtSøknadId,
            person = person,
            tilstandsType = "Journalført",
            dokument = null,
            journalpostId = "journalpostid",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk
        )

    private fun påbegyntGammelSøknad(påbegyntSøknadGammel: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = påbegyntSøknadGammel,
            person = person,
            tilstandsType = "Påbegynt",
            dokument = null,
            journalpostId = "1456",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk
        )
}

private fun endreSøknadOpprettet(søknadId: UUID, opprettetDato: LocalDateTime, ds: DataSource): Int {
    return using(sessionOf(ds)) {
        it.run(
            queryOf(
                "UPDATE soknad_v1 SET opprettet=:opprettet WHERE uuid=:uuid",
                mapOf("uuid" to søknadId.toString(), "opprettet" to opprettetDato)
            ).asUpdate
        )
    }
}

private class TestPersonVisitor(person: Person?) : PersonVisitor {
    init {
        person?.accept(this)
    }

    lateinit var søknader: List<Søknad>
    override fun visitPerson(ident: String, søknader: List<Søknad>) {
        this.søknader = søknader
    }
}
