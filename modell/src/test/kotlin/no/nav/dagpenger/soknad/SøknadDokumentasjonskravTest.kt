package no.nav.dagpenger.soknad

import de.slub.urn.URN
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

class SøknadDokumentasjonskravTest {
    private lateinit var søknad: Søknad
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

    @Test
    fun `håndter svar på dokumentasjonskrav`() {
        val søknadId = UUID.randomUUID()
        val ident = "12345678901"
        val språk = Språk(verdi = "NB")
        søknad = Søknad(
            søknadId = søknadId,
            språk = språk,
            ident = ident
        )
        søknad.håndter(
            ØnskeOmNySøknadHendelse(
                søknadId,
                språk.verdi.country,
                ident,
                prosessnavn = Prosessnavn("prosessnavn")
            )
        )
        søknad.håndter(SøknadOpprettetHendelse(Prosessversjon(Prosessnavn("prosessnavn"), 1), søknadId, ident))

        assertThrows<Aktivitetslogg.AktivitetException> {
            søknad.håndter(
                DokumentasjonIkkeTilgjengelig(
                    søknadId,
                    ident,
                    "1",
                    valg = Krav.Svar.SvarValg.SEND_SENERE,
                    begrunnelse = null
                )
            )
        }

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(0, this.aktiveDokumentKrav().size)
        }

        søknad.håndter(
            SøkeroppgaveHendelse(
                søknadId,
                ident,
                setOf(sannsynliggjøring)
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertEquals(Krav.Svar.SvarValg.IKKE_BESVART, krav.svar.valg)
                assertEquals(null, krav.svar.begrunnelse)
                assertEquals(0, krav.svar.filer.size)
            }
        }

        søknad.håndter(
            DokumentasjonIkkeTilgjengelig(
                søknadId,
                ident,
                "1",
                valg = Krav.Svar.SvarValg.SEND_SENERE,
                begrunnelse = "Har ikke"
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertEquals(Krav.Svar.SvarValg.SEND_SENERE, krav.svar.valg)
                assertEquals("Har ikke", krav.svar.begrunnelse)
                assertEquals(0, krav.svar.filer.size)
            }
        }
        val testFil = Krav.Fil(
            filnavn = "test.jpg",
            urn = URN.rfc8141().parse("urn:sid:1"),
            storrelse = 0,
            tidspunkt = ZonedDateTime.now(),
            bundlet = false
        )
        søknad.håndter(
            LeggTilFil(
                søknadId,
                ident,
                "1",
                fil = testFil
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertEquals(Krav.Svar.SvarValg.SEND_NÅ, krav.svar.valg)
                assertEquals(null, krav.svar.begrunnelse)
                assertEquals(1, krav.svar.filer.size)
                assertEquals(1, krav.svar.filer.size)
                assertEquals(testFil, krav.svar.filer.first())
            }
        }

        søknad.håndter(
            LeggTilFil(
                søknadId,
                ident,
                "1",
                fil = Krav.Fil(
                    filnavn = "test2.jpg",
                    urn = URN.rfc8141().parse("urn:sid:2"),
                    storrelse = 0,
                    tidspunkt = ZonedDateTime.now(),
                    bundlet = false
                )
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertEquals(Krav.Svar.SvarValg.SEND_NÅ, krav.svar.valg)
                assertEquals(null, krav.svar.begrunnelse)
                assertEquals(2, krav.svar.filer.size)
            }
        }

        søknad.håndter(
            SlettFil(
                søknadId,
                ident,
                "1",
                urn = URN.rfc8141().parse("urn:sid:2")
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertEquals(Krav.Svar.SvarValg.SEND_NÅ, krav.svar.valg)
                assertEquals(null, krav.svar.begrunnelse)
                assertEquals(1, krav.svar.filer.size)
            }
        }
        val bundleUrn = URN.rfc8141().parse("urn:sid:bundle")
        søknad.håndter(
            DokumentKravSammenstilling(
                søknadID = søknadId,
                ident = ident,
                kravId = "1",
                urn = bundleUrn
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertEquals(bundleUrn, krav.svar.bundle)
                assertEquals(1, krav.svar.filer.size)
                assertTrue(krav.svar.filer.first().bundlet)
            }
        }

        søknad.håndter(
            SøknadInnsendtHendelse(
                søknadID = søknadId, ident = ident
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertTrue(krav.svar.innsendt)
            }
        }

        val bundleUrn2 = URN.rfc8141().parse("urn:sid:bundle")
        søknad.håndter(
            DokumentKravSammenstilling(
                søknadID = søknadId, ident = ident, kravId = "1", urn = bundleUrn2
            )
        )

        with(TestSøknadInspektør2(søknad).dokumentkrav) {
            assertEquals(1, this.aktiveDokumentKrav().size)
            this.aktiveDokumentKrav().forEach { krav ->
                assertFalse(krav.svar.innsendt)
            }
        }
    }
}

internal class TestSøknadInspektør2(søknad: Søknad) : SøknadVisitor {
    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var dokumentkrav: Dokumentkrav
    internal lateinit var personLogg: Aktivitetslogg

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
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon
    ) {
        this.søknadId = søknadId
        this.dokumentkrav = dokumentkrav
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }
}
