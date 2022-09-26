package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Ettersending
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.NyInnsending
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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

    private val now = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    val ident = "12345678910"
    val søknad = Søknad.rehydrer(
        søknadId = søknadId,
        ident = ident,
        språk = Språk("NO"),
        dokumentkrav = Dokumentkrav.rehydrer(
            krav = setOf(krav)
        ),
        sistEndretAvBruker = now,
        tilstandsType = Søknad.Tilstand.Type.Påbegynt,
        aktivitetslogg = Aktivitetslogg(),
        null
    )

    @Test
    fun `Lagre og hente søknad med dokument, dokumentkrav, innsending og aktivitetslogg`() {
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Søknad.Tilstand.Type.Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            innsending = NyInnsending.rehydrer(
                UUID.randomUUID(),
                Innsending.InnsendingType.NY_DIALOG,
                now,
                "123123",
                Innsending.TilstandType.Journalført,
                Innsending.Dokument(
                    UUID.randomUUID(),
                    "navn",
                    "brevkode",
                    varianter = listOf(
                        Innsending.Dokument.Dokumentvariant(
                            UUID.randomUUID(),
                            "filnavn1",
                            "urn:burn:turn1",
                            "variant1",
                            "type1"
                        ),
                        Innsending.Dokument.Dokumentvariant(
                            UUID.randomUUID(),
                            "filnavn2",
                            "urn:burn:turn2",
                            "variant2",
                            "type2"
                        )
                    )
                ),
                listOf(
                    Innsending.Dokument(
                        UUID.randomUUID(),
                        "navn2",
                        "brevkode2",
                        varianter = listOf(
                            Innsending.Dokument.Dokumentvariant(
                                UUID.randomUUID(),
                                "filnavn3",
                                "urn:burn:turn3",
                                "variant3",
                                "type3"
                            ),
                            Innsending.Dokument.Dokumentvariant(
                                UUID.randomUUID(),
                                "filnavn4",
                                "urn:burn:turn4",
                                "variant4",
                                "type4"
                            )
                        )
                    )
                ),
                mutableListOf(
                    Ettersending.rehydrer(
                        UUID.randomUUID(),
                        Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG,
                        now,
                        null,
                        Innsending.TilstandType.Opprettet,
                        null,
                        listOf(),
                        Innsending.Brevkode("Tittel ettersending1", "0324-23")
                    ),
                    Ettersending.rehydrer(
                        UUID.randomUUID(),
                        Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG,
                        now,
                        null,
                        Innsending.TilstandType.AvventerJournalføring,
                        null,
                        listOf(),
                        Innsending.Brevkode("Tittel ettersending2", "0324-23")
                    )
                ),
                Innsending.Brevkode("Tittel", "04-02-03")
            )
        )

        withMigratedDb {
            SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)

                assertAntallRader("soknad_v1", 1)
                assertAntallRader("dokumentkrav_v1", 1)
                assertAntallRader("aktivitetslogg_v1", 1)
                assertAntallRader("innsending_v1", 3)
                assertAntallRader("dokument_v1", 2)
                assertAntallRader("hoveddokument_v1", 1)
                assertAntallRader("ettersending_v1", 2)
                assertAntallRader("dokumentvariant_v1", 4)
                val rehydrertSøknad: Søknad? = søknadPostgresRepository.hent(søknadId, ident)
                assertNotNull(rehydrertSøknad)

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
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Søknad.Tilstand.Type.Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            innsending = null
        )

        withMigratedDb {
            SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)
                val dokumentkrav = hentDokumentKrav(søknadPostgresRepository.hent(søknadId, ident)!!)

                val rehydrertSannsynliggjøring = dokumentkrav.aktiveDokumentKrav().first().sannsynliggjøring
                assertEquals(originalFaktumJson, rehydrertSannsynliggjøring.faktum().originalJson())
                assertEquals(
                    originalFaktumSomsannsynliggjøresFakta,
                    rehydrertSannsynliggjøring.sannsynliggjør().first().originalJson()
                )
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
            tidspunkt = now
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
        val tidspunkt = now
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
        assertTrue(expected.deepEquals(result), "Søknadene var ikke like. Forventet: $expected  Faktisk: $result")
    }
}
