package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import io.mockk.mockk
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
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

    val søknad = Søknad.rehydrer(
        søknadId = søknadId,
        søknadObserver = Søknadhåndterer(),
        ident = ident,
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
        sistEndretAvBruker = ZonedDateTime.now(),
        tilstandsType = "Journalført"
    )

    @Test
    fun hentDokumentkrav() {
        withMigratedDb {
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            søknadPostgresRepository.lagre(søknad)
            assertThrows<IkkeTilgangExeption> { søknadPostgresRepository.hentDokumentkravFor(søknadId, "ikke-tilgang") }

            val dokumentkrav = søknadPostgresRepository.hentDokumentkravFor(søknadId, ident)
            assertEquals(krav, dokumentkrav.aktiveDokumentKrav().first())
        }
    }

    @Test
    fun `Tilgangs kontroll til dokumentasjonskrav filer`() {
        val fil1 = Krav.Fil(
            filnavn = "ja.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/12345"),
            storrelse = 50000,
            tidspunkt = ZonedDateTime.now(),
        )
        withMigratedDb {
            val livssyklusPostgresRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            søknadPostgresRepository.lagre(søknad)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(),
                søknadCacheRepository = mockk(),
                livssyklusRepository = livssyklusPostgresRepository,
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = søknadPostgresRepository,
                søknadObservers = listOf(),

            )

            assertThrows<IkkeTilgangExeption> {
                søknadMediator.behandle(
                    SlettFil(
                        søknadID = søknadId,
                        ident = "1111",
                        kravId = "1",
                        urn = fil1.urn
                    )
                )
            }

            assertThrows<IkkeTilgangExeption> {
                søknadMediator.behandle(
                    LeggTilFil(
                        søknadID = søknadId,
                        ident = "1111",
                        kravId = "1",
                        fil = fil1
                    )
                )
            }
        }
    }

    @Test
    fun `livssyklus til dokumentasjonskrav svar`() {
        val tidspunkt = ZonedDateTime.now()
        val fil1 = Krav.Fil(
            filnavn = "ja.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/12345"),
            storrelse = 50000,
            tidspunkt = tidspunkt,
        )
        val fil2 = Krav.Fil(
            filnavn = "nei.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/45678"),
            storrelse = 50000,
            tidspunkt = tidspunkt,
        )
        withMigratedDb {
            val livssyklusPostgresRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            søknadPostgresRepository.lagre(søknad)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(),
                søknadCacheRepository = mockk(),
                livssyklusRepository = livssyklusPostgresRepository,
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = søknadPostgresRepository,
                søknadObservers = listOf(),

            )

            søknadMediator.hentDokumentkravFor(søknadId, ident).let {
                it.aktiveDokumentKrav().forEach { krav ->
                    assertTrue(krav.svar.filer.isEmpty())
                    assertEquals(Krav.Svar.SvarValg.IKKE_BESVART, krav.svar.valg)
                    assertNull(krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandle(
                LeggTilFil(
                    søknadId,
                    ident,
                    "1",
                    fil1
                )
            )

            søknadMediator.behandle(
                LeggTilFil(
                    søknadId,
                    ident,
                    "1",
                    fil2
                )
            )
            søknadMediator.behandle(
                LeggTilFil(
                    søknadId,
                    ident,
                    "1",
                    fil2
                )
            )
            søknadMediator.hentDokumentkravFor(søknadId, ident).let {
                assertEquals(2, it.aktiveDokumentKrav().first().svar.filer.size)
                it.aktiveDokumentKrav().forEach { krav ->
                    assertEquals(Krav.Svar.SvarValg.SEND_NÅ, krav.svar.valg)
                    assertNull(krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandle(
                DokumentasjonIkkeTilgjengelig(
                    søknadId,
                    ident,
                    "1",
                    valg = Krav.Svar.SvarValg.SEND_SENERE,
                    begrunnelse = "Har ikke"
                )
            )

            søknadMediator.hentDokumentkravFor(søknadId, ident).let {
                assertEquals(2, it.aktiveDokumentKrav().first().svar.filer.size)
                it.aktiveDokumentKrav().forEach { krav ->
                    assertEquals(Krav.Svar.SvarValg.SEND_SENERE, krav.svar.valg)
                    assertEquals("Har ikke", krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandle(
                SlettFil(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = fil1.urn
                )
            )
            søknadMediator.hentDokumentkravFor(søknadId, ident).let {
                assertEquals(1, it.aktiveDokumentKrav().first().svar.filer.size)
            }

            søknadMediator.behandle(
                SlettFil(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = fil2.urn
                )
            )

            søknadMediator.hentDokumentkravFor(søknadId, ident).let {
                assertEquals(0, it.aktiveDokumentKrav().first().svar.filer.size)
            }

            assertDoesNotThrow {
                søknadMediator.behandle(
                    SlettFil(
                        søknadID = søknadId,
                        ident = ident,
                        kravId = "1",
                        urn = fil2.urn
                    )
                )
            }
        }
    }
}
