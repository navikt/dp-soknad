package no.nav.dagpenger.soknad.innsending

import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.InnsendingVisitor
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class InnsendingPostgresRepositoryTest {
    private val testPersonIdent = "123"
    private val dialogId = UUID.randomUUID()

    private fun setup(test: () -> Unit) {
        withMigratedDb {
            SøknadPostgresRepository(dataSource = dataSource).lagre(
                søknad = Søknad(
                    søknadId = dialogId,
                    språk = Språk(verdi = "NO"),
                    ident = testPersonIdent,
                )
            )
            test()
        }
    }

    private val now =
        ZonedDateTime.of(LocalDateTime.of(2022, 1, 1, 1, 1), ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.MINUTES)
    private val hovedDokument = Innsending.Dokument(
        uuid = UUID.randomUUID(),
        kravId = "Hoveddokument",
        skjemakode = "NAV-04",
        varianter = listOf(
            Innsending.Dokument.Dokumentvariant(
                uuid = UUID.randomUUID(),
                filnavn = "filnavn1",
                urn = "urn:vedlegg:filnavn1",
                variant = "NETTO",
                type = "PDF"
            ),
            Innsending.Dokument.Dokumentvariant(
                uuid = UUID.randomUUID(),
                filnavn = "filnavn2",
                urn = "urn:vedlegg:filnavn2",
                variant = "NETTO",
                type = "JSON"
            )
        )
    )
    private val dokument1 = Innsending.Dokument(
        uuid = UUID.randomUUID(),
        kravId = "dokument1",
        skjemakode = "NAV-04",
        varianter = listOf(
            Innsending.Dokument.Dokumentvariant(
                uuid = UUID.randomUUID(),
                filnavn = "filnavn1",
                urn = "urn:vedlegg:filnavn1",
                variant = "NETTO",
                type = "PDF"
            ),
            Innsending.Dokument.Dokumentvariant(
                uuid = UUID.randomUUID(),
                filnavn = "filnavn2",
                urn = "urn:vedlegg:filnavn2",
                variant = "NETTO",
                type = "JSON"
            )
        )
    )

    private val dokument2 = Innsending.Dokument(
        uuid = UUID.randomUUID(),
        kravId = "dokument2",
        skjemakode = "NAV-04",
        varianter = listOf(
            Innsending.Dokument.Dokumentvariant(
                uuid = UUID.randomUUID(),
                filnavn = "filnavn1",
                urn = "urn:vedlegg:filnavn1",
                variant = "NETTO",
                type = "PDF"
            ),
            Innsending.Dokument.Dokumentvariant(
                uuid = UUID.randomUUID(),
                filnavn = "filnavn2",
                urn = "urn:vedlegg:filnavn2",
                variant = "NETTO",
                type = "JSON"
            )
        )
    )

    private val dokumenter = listOf(dokument1, dokument2)

    private val originalInnsending = Innsending.rehydrer(
        innsendingId = UUID.randomUUID(),
        type = Innsending.InnsendingType.NY_DIALOG,
        ident = testPersonIdent,
        søknadId = dialogId,
        innsendt = now,
        journalpostId = null,
        tilstandsType = Innsending.TilstandType.AvventerJournalføring,
        hovedDokument = hovedDokument,
        dokumenter = dokumenter,
        metadata = Innsending.Metadata("NAV-04")
    )

    @Test
    fun `CRUD operasjoner på innseniding`() {
        setup {
            val innsendingPostgresRepository = InnsendingPostgresRepository(dataSource)
            assertDoesNotThrow {
                innsendingPostgresRepository.lagre(originalInnsending)
                innsendingPostgresRepository.hent(originalInnsending.innsendingId).let { rehydrertInnsending ->
                    requireNotNull(rehydrertInnsending)
                    assertInnsendingEquals(originalInnsending, rehydrertInnsending)
                }
            }
        }
    }

    private fun assertInnsendingEquals(originalInnsending: Innsending, rehydrertInnsending: Innsending) {
        val expected = TestInnsendingVisitor(originalInnsending)
        val actual = TestInnsendingVisitor(rehydrertInnsending)

        assertEquals(expected.innsendingid, actual.innsendingid)
        assertEquals(expected.søknadid, actual.søknadid)
        assertEquals(expected.innsendingtype, actual.innsendingtype)
        assertEquals(expected.tilstand, actual.tilstand)
        assertEquals(expected.innsendt, actual.innsendt)
        assertEquals(expected.journalpost, actual.journalpost)
        assertEquals(expected.ident, actual.ident)
        assertEquals(expected.dokumenter, actual.dokumenter)
        assertEquals(expected.hoveddokument, actual.hoveddokument)
        assertEquals(expected.metadata, actual.metadata)
    }

    private class TestInnsendingVisitor(innsending: Innsending) : InnsendingVisitor {
        lateinit var ident: String
        lateinit var innsendingid: UUID
        lateinit var søknadid: UUID
        lateinit var innsendingtype: Innsending.InnsendingType
        lateinit var tilstand: Innsending.TilstandType
        lateinit var innsendt: ZonedDateTime
        var journalpost: String? = null
        var hoveddokument: Innsending.Dokument? = null
        lateinit var dokumenter: List<Innsending.Dokument>
        var metadata: Innsending.Metadata? = null

        init {
            innsending.accept(this)
        }

        override fun visit(
            innsendingId: UUID,
            søknadId: UUID,
            ident: String,
            innsendingType: Innsending.InnsendingType,
            tilstand: Innsending.TilstandType,
            innsendt: ZonedDateTime,
            journalpost: String?,
            hovedDokument: Innsending.Dokument?,
            dokumenter: List<Innsending.Dokument>,
            metadata: Innsending.Metadata?,
        ) {
            this.innsendingid = innsendingId
            this.søknadid = søknadId
            this.ident = ident
            this.innsendingtype = innsendingType
            this.tilstand = tilstand
            this.innsendt = innsendt
            this.journalpost = journalpost
            this.hoveddokument = hovedDokument
            this.dokumenter = dokumenter
            this.metadata = metadata
        }
    }
}
