package no.nav.dagpenger.soknad.innsending.meldinger

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.InnsendingVisitor
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.tjenester.NyInnsendingBehovMottak
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal class NyInnsendingBehovMottakTest {

    private val testRapid = TestRapid()
    private val slot = slot<NyInnsendingHendelse>()
    val mediator = mockk<InnsendingMediator>().also {
        every { it.behandle(capture(slot)) } just Runs
    }

    @Test
    fun `skal kunne lage NyInnsendingHendelse fra en JsonMessage pakke`() {
        val søknadId = UUID.randomUUID()
        val innsendtTidspunkt = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).toString()
        val dokumenter = listOf(
            Innsending.Dokument(
                uuid = UUID.randomUUID(),
                kravId = "k1",
                skjemakode = "s1",
                varianter = listOf(
                    Innsending.Dokument.Dokumentvariant(
                        uuid = UUID.randomUUID(),
                        filnavn = "f1",
                        urn = "urn:vedlegg:f1",
                        variant = "n1",
                        type = "t1"
                    ),
                    Innsending.Dokument.Dokumentvariant(
                        uuid = UUID.randomUUID(),
                        filnavn = "f2",
                        urn = "urn:vedlegg:f2",
                        variant = "n2",
                        type = "t2"
                    )
                )
            ),
            Innsending.Dokument(
                uuid = UUID.randomUUID(),
                kravId = null,
                skjemakode = null,
                varianter = listOf()
            )
        )
        val ident = "1234"
        val jsonMessage = JsonMessage.newMessage(
            map = mapOf(
                "@event_name" to "behov",
                "@behov" to listOf(NyInnsending.name),
                "søknad_uuid" to søknadId,
                "innsendtTidspunkt" to innsendtTidspunkt,
                "dokumentkrav" to dokumenter,
                "ident" to ident,
            )
        ).toJson()

        NyInnsendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator
        )

        testRapid.sendTestMessage(jsonMessage)

        verify(exactly = 1) { mediator.behandle(any<NyInnsendingHendelse>()) }
        TestInnsendingVisioter(slot.captured.innsending).let { innsending ->
            assertEquals(ident, innsending.ident)
            assertEquals(søknadId, innsending.søknadId)
            assertEquals(innsendtTidspunkt, innsending.innsendtTidspunkt.toString())
            assertEquals(dokumenter, innsending.dokumenter)
        }
    }
}

internal class TestInnsendingVisioter(innsending: Innsending) : InnsendingVisitor {
    lateinit var søknadId: UUID
    lateinit var innsendtTidspunkt: ZonedDateTime
    lateinit var ident: String
    lateinit var dokumenter: List<Innsending.Dokument>

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
        metadata: Innsending.Metadata?
    ) {
        this.søknadId = søknadId
        this.innsendtTidspunkt = innsendt
        this.ident = ident
        this.dokumenter = dokumenter
    }
}
