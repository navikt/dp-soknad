package no.nav.dagpenger.soknad.sletterutine

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
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
    private val nyPåbegyntSøknadUuid = UUID.randomUUID()
    private val innsendtSøknadUuid = UUID.randomUUID()
    private val testPersonIdent = "12345678910"
    private val syvDager = 7

    @Test
    fun `Sletter søknader og søknadcache med tilstand påbegynt etter gitt tidsinterval`() = withMigratedDb {
        val livssyklusRepository = LivssyklusPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource)
        val person = Person(testPersonIdent) {
            mutableListOf(
                gammelPåbegyntSøknad(gammelPåbegyntSøknadUuid, it),
                nyPåbegyntSøknad(nyPåbegyntSøknadUuid, it),
                innsendtSøknad(innsendtSøknadUuid, it)
            )
        }
        livssyklusRepository.lagre(person)
        søknadCacheRepository.lagre(TestSøkerOppgave(gammelPåbegyntSøknadUuid, testPersonIdent, "{}"))
        søknadCacheRepository.lagre(TestSøkerOppgave(nyPåbegyntSøknadUuid, testPersonIdent, "{}"))

        val nå = LocalDateTime.now()
        oppdaterFaktumSistEndret(gammelPåbegyntSøknadUuid, nå.minusDays(8), dataSource)
        oppdaterFaktumSistEndret(innsendtSøknadUuid, nå.minusDays(30), dataSource)
        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)

        assertCacheSlettet(gammelPåbegyntSøknadUuid, søknadCacheRepository)
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 2, person, livssyklusRepository)

        oppdaterFaktumSistEndret(nyPåbegyntSøknadUuid, nå.minusDays(8), dataSource)
        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)

        assertCacheSlettet(nyPåbegyntSøknadUuid, søknadCacheRepository)
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 1, person, livssyklusRepository)
    }

    private fun assertAtViIkkeSletterForMye(
        antallGjenværendeSøknader: Int,
        person: Person,
        livssyklusRepository: LivssyklusPostgresRepository
    ) {
        livssyklusRepository.hent(person.ident()).also { oppdatertPerson ->
            assertEquals(antallGjenværendeSøknader, TestPersonVisitor(oppdatertPerson).søknader.size)
        }
    }

    private fun assertCacheSlettet(søknadUuid: UUID, søknadCacheRepository: SøknadCachePostgresRepository) {
        assertThrows<NotFoundException> { søknadCacheRepository.hent(søknadUuid) }
    }

    private fun innsendtSøknad(journalførtSøknadId: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = journalførtSøknadId,
            person = person,
            tilstandsType = Journalført.name,
            dokument = null,
            journalpostId = "journalpostid",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk
        )

    private fun gammelPåbegyntSøknad(gammelPåbegyntSøknadId: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = gammelPåbegyntSøknadId,
            person = person,
            tilstandsType = Påbegynt.name,
            dokument = null,
            journalpostId = "1456",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk
        )

    private fun nyPåbegyntSøknad(nyPåbegyntSøknadId: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = nyPåbegyntSøknadId,
            person = person,
            tilstandsType = Påbegynt.name,
            dokument = null,
            journalpostId = "1457",
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
