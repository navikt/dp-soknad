package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import io.mockk.mockk
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.KravHendelse
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

internal class SøknadPostgresRepositoryTest {
    private val søknadId = UUID.randomUUID()

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
    )

    val ident = "12345678910"
    private val originalPerson = Person(ident) {

        mutableListOf(
            Søknad.rehydrer(
                søknadId = søknadId,
                person = it,
                tilstandsType = "Journalført",
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
                språk = Språk("NO"),
                dokumentkrav = Dokumentkrav.rehydrer(
                    krav = setOf(krav)
                ),
                sistEndretAvBruker = ZonedDateTime.now()
            )
        )
    }

    @Test
    fun hentDokumentkrav() {
        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).lagre(originalPerson)
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            assertThrows<IkkeTilgangExeption> { søknadPostgresRepository.hentDokumentkravFor(søknadId, "ikke-tilgang") }

            val dokumentkrav = søknadPostgresRepository.hentDokumentkravFor(søknadId, ident)
            assertEquals(krav, dokumentkrav.aktiveDokumentKrav().first())
        }
    }

    @Test
    fun `skriv fil til dokumentkrav`() {
        val tidspunkt = LocalDateTime.now()
        withMigratedDb {
            val livssyklusPostgresRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            livssyklusPostgresRepository.lagre(originalPerson)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(),
                søknadCacheRepository = mockk(),
                livssyklusRepository = livssyklusPostgresRepository,
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource),
                personObservers = listOf(),

                )

            søknadMediator.behandle(
                KravHendelse(
                    søknadId,
                    ident,
                    "1",
                    Krav.Fil(
                        filnavn = "ja.jpg",
                        urn = URN.rfc8141().parse("urn:vedlegg:1111/123234"),
                        storrelse = 50000,
                        tidspunkt = tidspunkt,

                        )

                )
            )

            with(søknadMediator.hentDokumentkravFor(søknadId, ident)) {
                assertTrue(this.aktiveDokumentKrav().first().filer.isNotEmpty())
            }

        }
    }
}
