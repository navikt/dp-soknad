package no.nav.dagpenger.soknad.db

import FerdigSøknadData
import no.nav.dagpenger.innsending.InnsendingPostgresRepository
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withCleanDb
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SoknadMigrationTest {
    private val søknadId1: UUID = UUID.randomUUID()
    private val søknadId2 = UUID.randomUUID()

    val now = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.MINUTES)
    private val søknader = listOf<Søknad>(
        Søknad.rehydrer(
            søknadId = søknadId1,
            ident = "123",
            opprettet = now,
            innsendt = null,
            språk = Språk(verdi = "NN"),
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.now(),
            tilstandsType = Søknad.Tilstand.Type.Innsendt,
            aktivitetslogg = Aktivitetslogg(forelder = null),
            prosessversjon = Prosessversjon("Dagpenger", 1),
            data = FerdigSøknadData,
        ),
        Søknad.rehydrer(
            søknadId = søknadId2,
            ident = "123",
            opprettet = ZonedDateTime.now(),
            innsendt = null,
            språk = Språk(verdi = "NN"),
            dokumentkrav = Dokumentkrav(),
            sistEndretAvBruker = ZonedDateTime.now(),
            tilstandsType = Søknad.Tilstand.Type.Innsendt,
            aktivitetslogg = Aktivitetslogg(forelder = null),
            prosessversjon = Prosessversjon("Dagpenger", 1),
            data = FerdigSøknadData,
        ),
    )

    private val innsendinger = listOf<Innsending>(
        Innsending.rehydrer(
            innsendingId = UUID.randomUUID(),
            type = Innsending.InnsendingType.NY_DIALOG,
            ident = "123",
            søknadId = søknadId1,
            innsendt = now,
            journalpostId = null,
            tilstandsType = Innsending.TilstandType.Journalført,
            hovedDokument = null,
            dokumenter = listOf(),
            metadata = null,
        ),
        Innsending.rehydrer(
            innsendingId = UUID.randomUUID(),
            type = Innsending.InnsendingType.NY_DIALOG,
            ident = "123",
            søknadId = søknadId1,
            innsendt = now,
            journalpostId = null,
            tilstandsType = Innsending.TilstandType.Journalført,
            hovedDokument = null,
            dokumenter = listOf(),
            metadata = null,
        ),
        Innsending.rehydrer(
            innsendingId = UUID.randomUUID(),
            type = Innsending.InnsendingType.NY_DIALOG,
            ident = "123",
            søknadId = søknadId2,
            innsendt = now,
            journalpostId = null,
            tilstandsType = Innsending.TilstandType.Journalført,
            hovedDokument = null,
            dokumenter = listOf(),
            metadata = null,
        ),
    )

    @Test
    fun `innsendt migrasjon`() {
        withCleanDb(
            target = "9",
            setup = {
                val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
                søknader.forEach { søknadPostgresRepository.lagre(it) }
                InnsendingPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repository ->
                    innsendinger.forEach { repository.lagre(it) }
                }

                assertNull(TestSøknadVisitor(søknadPostgresRepository.hent(søknadId1)!!).innsendt)
                assertNull(TestSøknadVisitor(søknadPostgresRepository.hent(søknadId2)!!).innsendt)
            },
        ) {
            SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repository ->
                assertEquals(now, TestSøknadVisitor(repository.hent(søknadId1)!!).innsendt)
                assertEquals(now, TestSøknadVisitor(repository.hent(søknadId2)!!).innsendt)
            }
        }
    }
}
