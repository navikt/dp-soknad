package no.nav.dagpenger.soknad.sletterutine

import io.ktor.server.plugins.NotFoundException
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøknadCachePostgresRepository
import no.nav.dagpenger.soknad.observers.SøknadSlettetObserver
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

internal class VaktmesterRepositoryTest {
    private val testRapid = TestRapid()
    private val språk = Språk("NO")
    private val gammelPåbegyntSøknadUuid = UUID.randomUUID()
    private val nyPåbegyntSøknadUuid = UUID.randomUUID()
    private val innsendtSøknadUuid = UUID.randomUUID()
    private val testPersonIdent = "12345678910"
    private val syvDager = 7

    @Test
    fun `Sletter all søknadsdata for påbegynte søknader uendret de siste 7 dagene`() = withMigratedDb {
        val livssyklusRepository = LivssyklusPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val søknadMediator = søknadMediator(søknadCacheRepository, livssyklusRepository)
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)
        val person = Person(testPersonIdent) {
            mutableListOf(
                gammelPåbegyntSøknad(gammelPåbegyntSøknadUuid, it),
                nyPåbegyntSøknad(nyPåbegyntSøknadUuid, it),
                innsendtSøknad(innsendtSøknadUuid, it)
            )
        }

        // TODO: Dette skal ikke være sånn
        livssyklusRepository.lagre(person)
        livssyklusRepository.lagre(person)

        søknadCacheRepository.lagre(TestSøkerOppgave(gammelPåbegyntSøknadUuid, testPersonIdent, "{}"))
        søknadCacheRepository.lagre(TestSøkerOppgave(nyPåbegyntSøknadUuid, testPersonIdent, "{}"))

        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)
        assertAntallSøknadSlettetEvent(1)
        assertAktivitetsloggSlettet(gammelPåbegyntSøknadUuid)
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 2, person, livssyklusRepository)
        assertCacheSlettet(gammelPåbegyntSøknadUuid, søknadCacheRepository)
    }

    private fun assertAktivitetsloggSlettet(søknadUuid: UUID) {
        val antallRader = using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    """
                            SELECT COUNT(*)
                                FROM aktivitetslogg_v2
                                WHERE data::jsonb -> 'aktiviteter' -> 0 -> 'kontekster' -> 0 -> 'kontekstMap' -> 'søknad_uuid' = '?'
                    """.trimIndent(),
                    søknadUuid.toString()

                ).map { row ->
                    row.int(1)
                }.asSingle
            )
        }

        assertEquals(0, antallRader)
    }

    private fun søknadMediator(
        søknadCacheRepository: SøknadCachePostgresRepository,
        livssyklusRepository: LivssyklusPostgresRepository
    ) = SøknadMediator(
        rapidsConnection = testRapid,
        søknadCacheRepository = søknadCacheRepository,
        livssyklusRepository = livssyklusRepository,
        søknadMalRepository = mockk(),
        ferdigstiltSøknadRepository = mockk(),
        søknadRepository = mockk(),
        personObservers = listOf(SøknadSlettetObserver(testRapid))

    )

    private fun assertAntallSøknadSlettetEvent(antall: Int) = assertEquals(antall, testRapid.inspektør.size)

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
            språk = språk,
            Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.now().minusDays(19)
        )

    private fun gammelPåbegyntSøknad(gammelPåbegyntSøknadId: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = gammelPåbegyntSøknadId,
            person = person,
            tilstandsType = Påbegynt.name,
            dokument = null,
            journalpostId = "1456",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk,
            Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.now().minusDays(10)
        )

    private fun nyPåbegyntSøknad(nyPåbegyntSøknadId: UUID, person: Person) =
        Søknad.rehydrer(
            søknadId = nyPåbegyntSøknadId,
            person = person,
            tilstandsType = Påbegynt.name,
            dokument = null,
            journalpostId = "1457",
            innsendtTidspunkt = null,
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.now().minusDays(1)
        )
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
