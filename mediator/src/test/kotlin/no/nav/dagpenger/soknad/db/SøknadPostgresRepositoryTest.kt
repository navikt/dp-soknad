package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

internal class SøknadPostgresRepositoryTest {
    private val søknadId = UUID.randomUUID()
    private val språk = Språk("NO")
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
        ident = ident,
        journalpost = Søknad.Journalpost(
            varianter = listOf(
                Søknad.Journalpost.Variant(
                    urn = "urn:soknad:fil1",
                    format = "ARKIV",
                    type = "PDF"
                ),
                Søknad.Journalpost.Variant(
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
        tilstandsType = Søknad.Tilstand.Type.Påbegynt,
        aktivitetslogg = Aktivitetslogg()
    )

    @Test
    fun `Lagre og hente søknad med dokument, dokumentkrav og aktivitetslogg`() {
        val søknadId = UUID.randomUUID()
        val ident = "12345678910"
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            journalpost = Søknad.Journalpost(
                varianter = listOf(
                    Søknad.Journalpost.Variant(
                        urn = "urn:soknad:fil1",
                        format = "ARKIV",
                        type = "PDF"
                    ),
                    Søknad.Journalpost.Variant(
                        urn = "urn:soknad:fil2",
                        format = "ARKIV",
                        type = "PDF"
                    )
                )
            ),
            journalpostId = "journalpostid",
            innsendtTidspunkt = ZonedDateTime.now(),
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = ZonedDateTime.now().minusDays(1),
            tilstandsType = Søknad.Tilstand.Type.Journalført,
            aktivitetslogg = Aktivitetslogg()
        )

        withMigratedDb {
            SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)

                assertAntallRader("soknad_v1", 1)
                assertAntallRader("dokumentkrav_v1", 1)
                assertAntallRader("aktivitetslogg_v3", 1)
                val rehydrertSøknad = søknadPostgresRepository.hent(søknadId, ident)

                assertDeepEquals(rehydrertSøknad!!, søknad)
                // TODO: Flytt tilgangskontroll til API-lag
                /*assertThrows<IkkeTilgangExeption> {
                    søknadPostgresRepository.hent(søknadId, "ikke-tilgang")
                }*/
            }
        }
    }

    @Test
    fun `dokumentkrav som sannsynliggjøres skal lagres med samme struktur`() {

        val originalFaktumJson = faktumJson("1", "f1")
        val dokumentFaktum =
            Faktum(originalFaktumJson)
        val originalFaktumSomsannsynliggjøresFakta = faktumJson("2", "f2")
        val faktaSomSannsynliggjøres =
            mutableSetOf(
                Faktum(originalFaktumSomsannsynliggjøresFakta)
            )
        val sannsynliggjøring = Sannsynliggjøring(
            id = dokumentFaktum.id,
            faktum = dokumentFaktum,
            sannsynliggjør = faktaSomSannsynliggjøres
        )
        val krav = Krav(
            sannsynliggjøring
        )

        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            journalpost = null,
            journalpostId = null,
            innsendtTidspunkt = null,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = ZonedDateTime.now().minusDays(1),
            tilstandsType = Søknad.Tilstand.Type.Påbegynt,
            aktivitetslogg = Aktivitetslogg()
        )

        withMigratedDb {
            SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)
                val dokumentkrav = hentDokumentKrav(søknadPostgresRepository.hent(søknadId, ident)!!)

                val rehydrertSannsynliggjøring = dokumentkrav.aktiveDokumentKrav().first().sannsynliggjøring
                assertEquals(originalFaktumJson, rehydrertSannsynliggjøring.faktum().originalJson())
                assertEquals(originalFaktumSomsannsynliggjøresFakta, rehydrertSannsynliggjøring.sannsynliggjør().first().originalJson())
            }
        }
    }

    @Test
    @Disabled("Må flyttes opp til API-laget")
    fun `Tilgangs kontroll til dokumentasjonskrav filer`() {
        val fil1 = Krav.Fil(
            filnavn = "ja.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/12345"),
            storrelse = 50000,
            tidspunkt = ZonedDateTime.now()
        )
        withMigratedDb {
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            søknadPostgresRepository.lagre(søknad)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(),
                søknadCacheRepository = mockk(),
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = søknadPostgresRepository,
                søknadObservers = listOf()
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
            tidspunkt = tidspunkt
        )
        val fil2 = Krav.Fil(
            filnavn = "nei.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/45678"),
            storrelse = 50000,
            tidspunkt = tidspunkt
        )
        withMigratedDb {
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            søknadPostgresRepository.lagre(søknad)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(),
                søknadCacheRepository = mockk(),
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = søknadPostgresRepository,
                søknadObservers = listOf()
            )

            hentDokumentKrav(søknadMediator.hent(søknadId, ident)!!).let {
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
            hentDokumentKrav(søknadMediator.hent(søknadId, ident)!!).let {
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

            hentDokumentKrav(søknadMediator.hent(søknadId, ident)!!).let {
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
            hentDokumentKrav(søknadMediator.hent(søknadId, ident)!!).let {
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

            hentDokumentKrav(søknadMediator.hent(søknadId, ident)!!).let {
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

    private fun hentDokumentKrav(søknad: Søknad): Dokumentkrav {
        class TestSøknadVisitor(søknad: Søknad) : SøknadVisitor {
            lateinit var dokumentKrav: Dokumentkrav

            init {
                søknad.accept(this)
            }

            override fun visitSøknad(
                søknadId: UUID,
                ident: String,
                tilstand: Søknad.Tilstand,
                journalpost: Søknad.Journalpost?,
                journalpostId: String?,
                innsendtTidspunkt: ZonedDateTime?,
                språk: Språk,
                dokumentkrav: Dokumentkrav,
                sistEndretAvBruker: ZonedDateTime?
            ) {
                this.dokumentKrav = dokumentkrav
            }
        }
        return TestSøknadVisitor(søknad).dokumentKrav
    }

    private fun assertAntallRader(tabell: String, antallRader: Int) {
        val faktiskeRader = using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("select count(1) from $tabell").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: $tabell")
    }

    private fun assertDeepEquals(expected: Søknad, result: Søknad) {
        assertTrue(expected.deepEquals(result), "Søknadene var ikke like")
    }
}
