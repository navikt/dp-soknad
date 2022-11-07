package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.DeepEquals.assertDeepEquals
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Ettersending
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.NyInnsending
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SøknadPostgresRepositoryTest {
    private val søknadId = UUID.randomUUID()
    private val språk = Språk("NO")
    private val dokumentFaktum =
        Faktum(faktumJson("1", "f1"))
    private val dokumentFaktum2 =
        Faktum(faktumJson("3", "f3"))
    private val faktaSomSannsynliggjøres =
        mutableSetOf(
            Faktum(faktumJson("2", "f2"))
        )
    private val faktaSomSannsynliggjøres2 =
        mutableSetOf(
            Faktum(faktumJson("4", "f4"))
        )
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val sannsynliggjøring2 = Sannsynliggjøring(
        id = dokumentFaktum2.id,
        faktum = dokumentFaktum2,
        sannsynliggjør = faktaSomSannsynliggjøres2
    )
    private val krav = Krav(
        sannsynliggjøring
    )
    private val opprettet = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    private val now = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    val ident = "12345678910"
    val søknad = Søknad.rehydrer(
        søknadId = søknadId,
        ident = ident,
        opprettet = opprettet,
        språk = Språk("NO"),
        dokumentkrav = Dokumentkrav.rehydrer(
            krav = setOf(krav)
        ),
        sistEndretAvBruker = now,
        tilstandsType = Påbegynt,
        aktivitetslogg = Aktivitetslogg(),
        null,
        null
    )

    @Test
    fun `Henting av eier til søknad`() {
        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                repository.lagre(søknad)

                assertEquals(ident, repository.hentEier(søknad.søknadUUID()))
                assertNull(repository.hentEier(UUID.randomUUID()))
            }
        }
    }

    @Test
    fun `Skal ikke hente søknader som har tilstand slettet`() {
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            språk = Språk("NO"),
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            null,
            null
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                søknad.håndter(
                    SlettSøknadHendelse(
                        søknadId,
                        ident
                    )
                )
                repository.lagre(søknad)

                assertEquals(0, repository.hentSøknader(ident).size)
                assertNull(repository.hent(søknadId))
            }
        }
    }

    @Test
    fun `Vi klarer å rehydrere innsendinger med dokumenter`() {
        /**
         * Denne framprovoserer problemene vi har hatt med at innsendinger rehydreres uten dokumenter
         */
        val krav1 = Krav(
            id = sannsynliggjøring.id,
            sannsynliggjøring = sannsynliggjøring,
            tilstand = Krav.KravTilstand.AKTIV,
            svar = Krav.Svar(
                filer = mutableSetOf(
                    Krav.Fil(
                        filnavn = "1-1.jpg",
                        urn = URN.rfc8141().parse("urn:nav:vedlegg:1-1"),
                        storrelse = 1000,
                        tidspunkt = now,
                        bundlet = false
                    )
                ),
                valg = Krav.Svar.SvarValg.SEND_NÅ,
                begrunnelse = null,
                bundle = URN.rfc8141().parse("urn:nav:bundle:1"),
                innsendt = true
            )
        )
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav1)
            ),
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            innsending = NyInnsending.rehydrer(
                innsendingId = UUID.randomUUID(),
                type = Innsending.InnsendingType.NY_DIALOG,
                innsendt = now,
                journalpostId = "123123",
                tilstandsType = Innsending.TilstandType.AvventerArkiverbarSøknad,
                hovedDokument = null,
                dokumenter = listOf(
                    Innsending.Dokument(
                        uuid = UUID.randomUUID(),
                        brevkode = "brevkode-vedlegg",
                        varianter = listOf(
                            Innsending.Dokument.Dokumentvariant(
                                UUID.randomUUID(),
                                "filnavn3",
                                "urn:burn:turn3",
                                "variant3",
                                "type3"
                            )
                        ),
                        kravId = "kravId"
                    )
                ),
                ettersendinger = mutableListOf(),
                metadata = Innsending.Metadata("04-02-03", "en tittel")
            ),
            prosessversjon = null
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)

                assertAntallRader("soknad_v1", 1)
                assertAntallRader("dokumentkrav_filer_v1", 1)
                assertAntallRader("dokumentkrav_v1", 1)
                assertAntallRader("aktivitetslogg_v1", 1)
                assertAntallRader("innsending_v1", 1)
                assertAntallRader("dokument_v1", 1)
                assertAntallRader("hoveddokument_v1", 0)
                assertAntallRader("ettersending_v1", 0)
                assertAntallRader("dokumentvariant_v1", 1)

                søknadPostgresRepository.hent(søknadId).let { rehydrertSøknad ->
                    assertNotNull(rehydrertSøknad)
                    assertDeepEquals(rehydrertSøknad, søknad)
                    rehydrertSøknad?.accept(object : SøknadVisitor {
                        override fun visit(
                            innsendingId: UUID,
                            innsending: Innsending.InnsendingType,
                            tilstand: Innsending.TilstandType,
                            innsendt: ZonedDateTime,
                            journalpost: String?,
                            hovedDokument: Innsending.Dokument?,
                            dokumenter: List<Innsending.Dokument>,
                            metadata: Innsending.Metadata?
                        ) {
                            assertEquals(1, dokumenter.size)
                            assertEquals("brevkode-vedlegg", dokumenter.first().brevkode)
                            assertEquals("kravId", dokumenter.first().kravId)
                            assertEquals(1, dokumenter.first().varianter.size)
                        }
                    })
                }
            }
        }
    }

    @Test
    fun `Lagre og hente søknad med dokument, dokumentkrav, innsending og aktivitetslogg`() {
        val krav1 = Krav(
            id = sannsynliggjøring.id,
            sannsynliggjøring = sannsynliggjøring,
            tilstand = Krav.KravTilstand.AKTIV,
            svar = Krav.Svar(
                filer = mutableSetOf(
                    Krav.Fil(
                        filnavn = "1-1.jpg",
                        urn = URN.rfc8141().parse("urn:nav:vedlegg:1-1"),
                        storrelse = 1000,
                        tidspunkt = now,
                        bundlet = false,
                    ),
                    Krav.Fil(
                        filnavn = "1-2.jpg",
                        urn = URN.rfc8141().parse("urn:nav:vedlegg:1-2"),
                        storrelse = 1000,
                        tidspunkt = now,
                        bundlet = false
                    )
                ),
                valg = Krav.Svar.SvarValg.SEND_NÅ,
                begrunnelse = null,
                bundle = URN.rfc8141().parse("urn:nav:bundle:1"),
                innsendt = false
            )
        )
        val krav2 = Krav(
            id = sannsynliggjøring2.id,
            sannsynliggjøring = sannsynliggjøring2,
            tilstand = Krav.KravTilstand.AKTIV,
            svar = Krav.Svar(
                filer = mutableSetOf(
                    Krav.Fil(
                        filnavn = "2.jpg",
                        urn = URN.rfc8141().parse("urn:nav:vedlegg:2"),
                        storrelse = 1000,
                        tidspunkt = now,
                        bundlet = false
                    )
                ),
                valg = Krav.Svar.SvarValg.SEND_NÅ,
                begrunnelse = null,
                bundle = URN.rfc8141().parse("urn:nav:bundle:2"),
                innsendt = false
            )
        )
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav1, krav2)
            ),
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            innsending = NyInnsending.rehydrer(
                UUID.randomUUID(),
                Innsending.InnsendingType.NY_DIALOG,
                now,
                "123123",
                Innsending.TilstandType.AvventerArkiverbarSøknad,
                Innsending.Dokument(
                    uuid = UUID.randomUUID(),
                    brevkode = "brevkode",
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
                    ),
                    kravId = null
                ),
                listOf(
                    Innsending.Dokument(
                        uuid = UUID.randomUUID(),
                        brevkode = "brevkode2",
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
                        ),
                        kravId = "kravId"
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
                        Innsending.Metadata("0324-23")
                    ),
                    Ettersending.rehydrer(
                        UUID.randomUUID(),
                        Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG,
                        now,
                        null,
                        Innsending.TilstandType.AvventerJournalføring,
                        null,
                        listOf(),
                        Innsending.Metadata(tittel = "0324-23")
                    )
                ),
                Innsending.Metadata("04-02-03")
            ),
            prosessversjon = null
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)

                assertAntallRader("soknad_v1", 1)
                assertAntallRader("dokumentkrav_filer_v1", 3)
                assertAntallRader("dokumentkrav_v1", 2)
                assertAntallRader("aktivitetslogg_v1", 1)
                assertAntallRader("innsending_v1", 3)
                assertAntallRader("dokument_v1", 2)
                assertAntallRader("hoveddokument_v1", 1)
                assertAntallRader("ettersending_v1", 2)
                assertAntallRader("dokumentvariant_v1", 4)

                søknadPostgresRepository.hent(søknadId).let { rehydrertSøknad ->
                    assertNotNull(rehydrertSøknad)
                    assertDeepEquals(rehydrertSøknad, søknad)
                }

                søknadPostgresRepository.lagre(søknad)

                søknadPostgresRepository.hent(søknadId).let { rehydrertSøknad ->
                    assertNotNull(rehydrertSøknad)
                    assertDeepEquals(rehydrertSøknad, søknad)
                    rehydrertSøknad?.accept(object : SøknadVisitor {
                        override fun visit(
                            innsendingId: UUID,
                            innsending: Innsending.InnsendingType,
                            tilstand: Innsending.TilstandType,
                            innsendt: ZonedDateTime,
                            journalpost: String?,
                            hovedDokument: Innsending.Dokument?,
                            dokumenter: List<Innsending.Dokument>,
                            metadata: Innsending.Metadata?
                        ) {
                            if (innsending == Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG) return
                            assertEquals(2, hovedDokument!!.varianter.size)
                            assertEquals(1, dokumenter.size)
                        }
                    })
                }
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
            opprettet = opprettet,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            innsending = null,
            prosessversjon = null
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)
                val dokumentkrav = hentDokumentKrav(søknadPostgresRepository.hent(søknadId)!!)
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
    fun `livssyklus til dokumentasjonskrav svar`() {
        val tidspunkt = now
        val fil1 = Krav.Fil(
            filnavn = "ja.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/12345"),
            storrelse = 50000,
            tidspunkt = tidspunkt,
            bundlet = false
        )
        val fil2 = Krav.Fil(
            filnavn = "nei.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/45678"),
            storrelse = 50000,
            tidspunkt = tidspunkt,
            bundlet = false
        )
        withMigratedDb {
            val søknadPostgresRepository = SøknadPostgresRepository(dataSource)
            søknadPostgresRepository.lagre(søknad)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(relaxed = true),
                søknadDataRepository = mockk(),
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = søknadPostgresRepository,
                søknadObservers = listOf()
            )

            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let {
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
            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let {
                assertEquals(2, it.aktiveDokumentKrav().first().svar.filer.size)
                it.aktiveDokumentKrav().first().svar.filer.forEach {
                    assertFalse(it.bundlet)
                }
                it.aktiveDokumentKrav().forEach { krav ->
                    assertEquals(Krav.Svar.SvarValg.SEND_NÅ, krav.svar.valg)
                    assertNull(krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandle(
                DokumentKravSammenstilling(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = URN.rfc8141().parse("urn:vedlegg:bundle")
                )
            )

            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let { dokumentkrav ->
                val filer = dokumentkrav.aktiveDokumentKrav().first().svar.filer
                assertEquals(2, filer.size)
                filer.forEach { fil ->
                    assertTrue(fil.bundlet)
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

            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let {
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
            assertEquals(
                1,
                hentDokumentKrav(søknadMediator.hent(søknadId)!!).aktiveDokumentKrav().first().svar.filer.size
            )

            søknadMediator.behandle(
                SlettFil(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = fil2.urn
                )
            )

            assertEquals(
                0,
                hentDokumentKrav(søknadMediator.hent(søknadId)!!).aktiveDokumentKrav().first().svar.filer.size
            )

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

    @Test
    fun `Skal kunne hente ut en påbegynt søknad`() {
        val søknadIdForInnsendt = UUID.randomUUID()
        val innsendtSøknad = Søknad.rehydrer(
            søknadId = søknadIdForInnsendt,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            språk = Språk("NO"),
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav)
            ),
            sistEndretAvBruker = now,
            tilstandsType = Innsendt,
            aktivitetslogg = Aktivitetslogg(),
            null,
            null
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                repository.lagre(innsendtSøknad)
                repository.lagre(søknad)
                val hentetPåbegyntSøknad = repository.hentPåbegyntSøknad(ident)
                assertNotNull(hentetPåbegyntSøknad)
                assertDeepEquals(søknad, hentetPåbegyntSøknad)
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
                opprettet: ZonedDateTime,
                tilstand: Søknad.Tilstand,
                språk: Språk,
                dokumentkrav: Dokumentkrav,
                sistEndretAvBruker: ZonedDateTime
            ) {
                this.dokumentKrav = dokumentkrav
            }
        }
        return TestSøknadVisitor(søknad).dokumentKrav
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
}
