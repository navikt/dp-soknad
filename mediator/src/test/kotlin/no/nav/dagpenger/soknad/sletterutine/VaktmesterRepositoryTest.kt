package no.nav.dagpenger.soknad.sletterutine

import FerdigSøknadData
import de.slub.urn.URN
import io.ktor.server.plugins.NotFoundException
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
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
import java.time.LocalDateTime
import java.time.ZoneId
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
    private val dokumentFaktum =
        Faktum(faktumJson("1", "f1"))
    private val faktaSomSannsynliggjøres =
        mutableSetOf(
            Faktum(faktumJson("2", "f2"))
        )
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val krav = Krav(
        sannsynliggjøring
    ).also {
        it.svar.filer.add(
            Krav.Fil(
                filnavn = "test.jpg",
                urn = URN.rfc8141().parse("urn:test:1"),
                storrelse = 0,
                tidspunkt = ZonedDateTime.now(),
                bundlet = false
            )
        )
    }

    @Test
    fun `Låsen hindrer parallel slettinger`() = withMigratedDb {
        val søknadRepository = SøknadPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadDataPostgresRepository(dataSource)
        val søknadMediator = søknadMediator(
            søknadCacheRepository = søknadCacheRepository,
            søknadRepository = søknadRepository
        )
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)

        using(sessionOf(dataSource)) { session ->
            session.lås(låseNøkkel)
            assertNull(vaktmesterRepository.slett())
            session.låsOpp(låseNøkkel)
            assertNotNull(vaktmesterRepository.slett())
        }
    }

    @Test
    fun `Markerer påbegynte søknader uendret de siste 7 dagene som sletta`() = withMigratedDb {
        val søknadRepository = SøknadPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadDataPostgresRepository(dataSource)
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

        vaktmesterRepository.markerUtdaterteTilSletting(syvDager)
        assertAntallSøknadSlettetEvent(1)
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 2, søknadRepository, testPersonIdent)
    }

    @Test
    fun `Sletter all søknadsdata for slettede søknader`() = withMigratedDb {
        val søknadRepository = SøknadPostgresRepository(dataSource)
        val søknadCacheRepository = SøknadDataPostgresRepository(dataSource)
        val søknadMediator = søknadMediator(
            søknadCacheRepository = søknadCacheRepository,
            søknadRepository = søknadRepository
        )
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)

        søknadRepository.lagre(gammelPåbegyntSøknad(gammelPåbegyntSøknadUuid, testPersonIdent))
        søknadRepository.lagre(nyPåbegyntSøknad(nyPåbegyntSøknadUuid, testPersonIdent))
        søknadRepository.lagre(innsendtSøknad(innsendtSøknadUuid, testPersonIdent))
        søknadCacheRepository.lagre(TestSøkerOppgave(gammelPåbegyntSøknadUuid, testPersonIdent, "{}"))
        søknadCacheRepository.lagre(TestSøkerOppgave(nyPåbegyntSøknadUuid, testPersonIdent, "{}"))

        settSlettet(gammelPåbegyntSøknadUuid)

        assertAntallRader("soknad_v1", 3)
        assertAntallRader("dokumentkrav_filer_v1", 1)
        assertAntallRader("dokumentkrav_v1", 1)
        assertAntallRader("aktivitetslogg_v1", 3)

        vaktmesterRepository.slett()
        assertAtViIkkeSletterForMye(antallGjenværendeSøknader = 2, søknadRepository, testPersonIdent)
        assertCacheSlettet(gammelPåbegyntSøknadUuid, søknadCacheRepository)
        assertFalse(aktivitetsloggFinnes(gammelPåbegyntSøknadUuid))

        assertAntallRader("soknad_v1", 2)
        assertAntallRader("dokumentkrav_filer_v1", 0)
        assertAntallRader("dokumentkrav_v1", 0)
        assertAntallRader("aktivitetslogg_v1", 2)
    }

    private fun assertAntallRader(tabell: String, antallRader: Int) {
        val faktiskeRader = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("select count(1) from $tabell").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: $tabell")
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

    private fun settSlettet(uuid: UUID) = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf( //language=PostgreSQL
                "UPDATE soknad_v1 SET tilstand = '${Tilstand.Type.Slettet}' WHERE uuid = ?",
                uuid
            ).asUpdate
        )
    }

    private fun søknadMediator(
        søknadCacheRepository: SøknadDataPostgresRepository,
        søknadRepository: SøknadRepository
    ) = SøknadMediator(
        rapidsConnection = testRapid,
        søknadDataRepository = søknadCacheRepository,
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

    private fun assertCacheSlettet(søknadUuid: UUID, søknadCacheRepository: SøknadDataPostgresRepository) {
        assertThrows<NotFoundException> { søknadCacheRepository.hentSøkerOppgave(søknadUuid) }
    }

    private fun aktivitetsloggFinnes(søknadUuid: UUID): Boolean {
        val antallRader = using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    "SELECT COUNT(*) FROM aktivitetslogg_v1 WHERE soknad_uuid = ?",
                    søknadUuid
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
            opprettet = ZonedDateTime.now(),
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.of(LocalDateTime.of(2022, 11, 2, 2, 2, 2, 2), ZoneId.of("Europe/Oslo")),
            tilstandsType = Innsendt,
            aktivitetslogg = Aktivitetslogg(),
            null,
            null,
            FerdigSøknadData,
            0
        )

    private fun gammelPåbegyntSøknad(gammelPåbegyntSøknadId: UUID, ident: String) =
        Søknad.rehydrer(
            søknadId = gammelPåbegyntSøknadId,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                setOf(krav)
            ),
            sistEndretAvBruker = ZonedDateTime.of(LocalDateTime.of(2022, 11, 2, 2, 2, 2, 2), ZoneId.of("Europe/Oslo")),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            null,
            null,
            FerdigSøknadData,
            0
        )

    private fun nyPåbegyntSøknad(nyPåbegyntSøknadId: UUID, ident: String) =
        Søknad.rehydrer(
            søknadId = nyPåbegyntSøknadId,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            språk = språk,
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.of(LocalDateTime.of(2022, 11, 2, 2, 2, 2, 2), ZoneId.of("Europe/Oslo")),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            null,
            null,
            FerdigSøknadData,
            0
        )
}
