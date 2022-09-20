package no.nav.dagpenger.soknad.sletterutine

import io.ktor.server.plugins.NotFoundException
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøknadCachePostgresRepository
import no.nav.dagpenger.soknad.observers.SøknadSlettetObserver
import no.nav.dagpenger.soknad.sletterutine.VaktmesterPostgresRepository.Companion.låseNøkkel
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `Søknadsdata for påbegynte søknader uendret de siste 7 dagene`() = withMigratedDb {
        val søknadRepository = SøknadPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val søknadMediator = søknadMediator(
            søknadCacheRepository = søknadCacheRepository,
            søknadRepository = søknadRepository
        )
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
        val søknadRepository = SøknadPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadCachePostgresRepository(dataSource)
        val søknadMediator = søknadMediator(
            søknadCacheRepository = søknadCacheRepository,
            søknadRepository = søknadRepository
        )
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)

        søknadRepository.lagre(gammelPåbegyntSøknad(gammelPåbegyntSøknadUuid, testPersonIdent))
        søknadRepository.lagre(nyPåbegyntSøknad(nyPåbegyntSøknadUuid, testPersonIdent))
        søknadRepository.lagre(innsendtSøknad(innsendtSøknadUuid, testPersonIdent))
        assertTrue(aktivitetsloggFinnes(gammelPåbegyntSøknadUuid))

        settSistEndretAvBruker(antallDagerSiden = 8, gammelPåbegyntSøknadUuid)
        settSistEndretAvBruker(antallDagerSiden = 2, nyPåbegyntSøknadUuid)
        settSistEndretAvBruker(antallDagerSiden = 30, innsendtSøknadUuid)

        søknadCacheRepository.lagre(TestSøkerOppgave(gammelPåbegyntSøknadUuid, testPersonIdent, "{}"))
        søknadCacheRepository.lagre(TestSøkerOppgave(nyPåbegyntSøknadUuid, testPersonIdent, "{}"))

        vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(syvDager)
        assertAntallSøknadSlettetEvent(1)
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 2, søknadRepository, testPersonIdent)
        assertCacheSlettet(gammelPåbegyntSøknadUuid, søknadCacheRepository)
        assertFalse(aktivitetsloggFinnes(gammelPåbegyntSøknadUuid))
    }

    private fun settSistEndretAvBruker(antallDagerSiden: Int, uuid: UUID) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    "UPDATE soknad_v1 SET sist_endret_av_bruker = (NOW() - INTERVAL '$antallDagerSiden DAYS') WHERE uuid = ?",
                    uuid
                ).asUpdate
            )
        }
    }

    private fun søknadMediator(
        søknadCacheRepository: SøknadCachePostgresRepository,
        søknadRepository: SøknadRepository
    ) = SøknadMediator(
        rapidsConnection = testRapid,
        søknadCacheRepository = søknadCacheRepository,
        søknadMalRepository = mockk(),
        ferdigstiltSøknadRepository = mockk(),
        søknadRepository = søknadRepository,
        søknadObservers = listOf(SøknadSlettetObserver(testRapid))
    )

    private fun assertAntallSøknadSlettetEvent(antall: Int) = assertEquals(antall, testRapid.inspektør.size)

    private fun assertAtViIkkeSletterForMye(
        antallGjenværendeSøknader: Int,
        søknadRepository: SøknadRepository,
        ident: String
    ) {
        søknadRepository.hentSøknader(ident).also { søknader ->
            assertEquals(antallGjenværendeSøknader, søknader.size)
        }
    }

    private fun assertCacheSlettet(søknadUuid: UUID, søknadCacheRepository: SøknadCachePostgresRepository) {
        assertThrows<NotFoundException> { søknadCacheRepository.hent(søknadUuid) }
    }

    private fun aktivitetsloggFinnes(søknadUuid: UUID): Boolean {
        val antallRader = using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    "SELECT COUNT(*) FROM aktivitetslogg_v1 WHERE soknad_uuid = ?", søknadUuid
                ).map { row ->
                    row.int(1)
                }.asSingle
            )
        }

        return antallRader == 1
    }

    private fun innsendtSøknad(journalførtSøknadId: UUID, ident: String) =
        Søknad.rehydrer(
            søknadId = journalførtSøknadId,
            ident = ident,
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = null,
            tilstandsType = Innsendt,
            aktivitetslogg = Aktivitetslogg()
        )

    private fun gammelPåbegyntSøknad(gammelPåbegyntSøknadId: UUID, ident: String) =
        Søknad.rehydrer(
            søknadId = gammelPåbegyntSøknadId,
            ident = ident,
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = null,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg()
        )

    private fun nyPåbegyntSøknad(nyPåbegyntSøknadId: UUID, ident: String) =
        Søknad.rehydrer(
            søknadId = nyPåbegyntSøknadId,
            ident = ident,
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = null,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg()
        )
}
