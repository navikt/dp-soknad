package no.nav.dagpenger.soknad.sletterutine

import io.ktor.server.plugins.NotFoundException
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøknadCachePostgresRepository
import no.nav.dagpenger.soknad.observers.SøknadSlettetObserver
import no.nav.dagpenger.soknad.sletterutine.VaktmesterPostgresRepository.Companion.låseNøkkel
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `Ssøknadsdata for påbegynte søknader uendret de siste 7 dagene`() = withMigratedDb {
        val livssyklusRepository = LivssyklusPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val søknadMediator = søknadMediator(søknadCacheRepository, livssyklusRepository)
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)

        using(sessionOf(dataSource)) { session ->
            session.lås(låseNøkkel)
            assertNull(vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager))
            session.låsOpp(låseNøkkel)
            assertNotNull(vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager))
        }
    }

    @Test
    fun `Sletter all søknadsdata for påbegynte søknader uendret de siste 7 dagene`() = withMigratedDb {
        val livssyklusRepository = LivssyklusPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val søknadMediator = søknadMediator(søknadCacheRepository, livssyklusRepository)
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)
        val søknadhåndterer = Søknadhåndterer(testPersonIdent) {
            mutableListOf(
                gammelPåbegyntSøknad(gammelPåbegyntSøknadUuid, it),
                nyPåbegyntSøknad(nyPåbegyntSøknadUuid, it),
                innsendtSøknad(innsendtSøknadUuid, it)
            )
        }

        livssyklusRepository.lagre(søknadhåndterer)
        settSistEndretAvBruker(antallDagerSiden = 8, gammelPåbegyntSøknadUuid)
        settSistEndretAvBruker(antallDagerSiden = 2, nyPåbegyntSøknadUuid)
        settSistEndretAvBruker(antallDagerSiden = 30, innsendtSøknadUuid)

        søknadCacheRepository.lagre(TestSøkerOppgave(gammelPåbegyntSøknadUuid, testPersonIdent, "{}"))
        søknadCacheRepository.lagre(TestSøkerOppgave(nyPåbegyntSøknadUuid, testPersonIdent, "{}"))

        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)
        assertAntallSøknadSlettetEvent(1)
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 2, søknadhåndterer, livssyklusRepository)
        assertCacheSlettet(gammelPåbegyntSøknadUuid, søknadCacheRepository)
        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)
        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)
    }

    private fun settSistEndretAvBruker(antallDagerSiden: Int, uuid: UUID) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    "UPDATE soknad_v1 SET sist_endret_av_bruker = (NOW() - INTERVAL '$antallDagerSiden DAYS') WHERE uuid = ?",
                    uuid.toString()
                ).asUpdate
            )
        }
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
        søknadhåndterer: Søknadhåndterer,
        livssyklusRepository: LivssyklusPostgresRepository
    ) {
        livssyklusRepository.hent(søknadhåndterer.ident()).also { oppdatertPerson ->
            assertEquals(antallGjenværendeSøknader, TestPersonVisitor(oppdatertPerson).søknader.size)
        }
    }

    private fun assertCacheSlettet(søknadUuid: UUID, søknadCacheRepository: SøknadCachePostgresRepository) {
        assertThrows<NotFoundException> { søknadCacheRepository.hent(søknadUuid) }
    }

    private fun innsendtSøknad(journalførtSøknadId: UUID, søknadhåndterer: Søknadhåndterer) =
        Søknad.rehydrer(
            søknadId = journalførtSøknadId,
            søknadhåndterer = søknadhåndterer,
            tilstandsType = Journalført.name,
            dokument = null,
            journalpostId = "journalpostid",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk,
            Dokumentkrav(),
            sistEndretAvBruker = null
        )

    private fun gammelPåbegyntSøknad(gammelPåbegyntSøknadId: UUID, søknadhåndterer: Søknadhåndterer) =
        Søknad.rehydrer(
            søknadId = gammelPåbegyntSøknadId,
            søknadhåndterer = søknadhåndterer,
            tilstandsType = Påbegynt.name,
            dokument = null,
            journalpostId = "1456",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk,
            Dokumentkrav(),
            sistEndretAvBruker = null
        )

    private fun nyPåbegyntSøknad(nyPåbegyntSøknadId: UUID, søknadhåndterer: Søknadhåndterer) =
        Søknad.rehydrer(
            søknadId = nyPåbegyntSøknadId,
            søknadhåndterer = søknadhåndterer,
            tilstandsType = Påbegynt.name,
            dokument = null,
            journalpostId = "1457",
            innsendtTidspunkt = null,
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = null
        )
}

private class TestPersonVisitor(søknadhåndterer: Søknadhåndterer?) : PersonVisitor {
    init {
        søknadhåndterer?.accept(this)
    }

    lateinit var søknader: List<Søknad>
    override fun visitPerson(ident: String, søknader: List<Søknad>) {
        this.søknader = søknader
    }
}
