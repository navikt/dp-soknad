package no.nav.dagpenger.soknad.sletterutine

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
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

internal class VaktmesterRepositoryTest {
    private val språk = Språk("NO")
    private val gammelPåbegyntSøknadUuid = UUID.randomUUID()
    private val journalførtSøknadUuid = UUID.randomUUID()
    private val testPersonIdent = "12345678910"
    private val SYV_DAGER = 7

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
        oppdaterFaktumSistEndret(gammelPåbegyntSøknadUuid, nå.minusDays(30), dataSource)
        oppdaterFaktumSistEndret(journalførtSøknadUuid, nå.minusDays(30), dataSource)

        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
        // println(livssyklusRepository.hent(testPersonIdent))
        // assertEquals(1, harSlettetRad)
        søknadCacheRepository.hent(journalførtSøknadUuid)
        // assertThrows<NotFoundException> { søknadCacheRepository.hent(gammelPåbegyntSøknadUuid) }

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

private fun oppdaterFaktumSistEndret(søknadId: UUID, faktumSistEndretDato: LocalDateTime, ds: DataSource): Int {
    return using(sessionOf(ds)) {
        it.run(
            queryOf(
                //language=PostgreSQL
                "UPDATE soknad_cache SET faktum_sist_endret=:faktum_sist_endret WHERE uuid=:uuid",
                mapOf("uuid" to søknadId.toString(), "faktum_sist_endret" to faktumSistEndretDato)
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
