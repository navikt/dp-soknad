package no.nav.dagpenger.soknad.innsending.meldinger

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.innsending.InnsendingMediator
import no.nav.dagpenger.innsending.tjenester.NyEttersendingBehovMottak
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID

internal class NyEttersendingBehovMottakTest {
    companion object {
        @JvmStatic
        fun testFixtures(): List<TestFixture> {
            return listOf(
                TestFixture(
                    behov = listOf(Behovtype.NyEttersending),
                    innsendingType = ETTERSENDING_TIL_DIALOG,
                ),
                TestFixture(
                    behov = listOf(Behovtype.NyEttersending, Behovtype.InnsendingMetadata),
                    innsendingType = ETTERSENDING_TIL_DIALOG,
                ),
            )
        }
    }

    private val testRapid = TestRapid()
    private val slot = slot<NyInnsendingHendelse>()
    private val mediator = mockk<InnsendingMediator>().also {
        every { it.behandleNyInnsendingHendelse(capture(slot)) } just Runs
    }

    @BeforeEach
    fun setup() = testRapid.reset()

    @ParameterizedTest
    @MethodSource("testFixtures")
    fun `Skal håndtere NyInnsending hendelse`(testFixture: TestFixture) {
        NyEttersendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator,
        )

        testRapid.sendTestMessage(
            lagTestJson(fixture = testFixture),
        )

        verify(exactly = 1) { mediator.behandleNyInnsendingHendelse(any<NyInnsendingHendelse>()) }
        TestInnsendingVisitor(slot.captured.innsending).let { innsending ->
            assertEquals(testFixture.ident, innsending.ident)
            assertEquals(testFixture.søknadId, innsending.søknadId)
            assertEquals(testFixture.innsendtTidspunkt, innsending.innsendtTidspunkt.toString())
            assertEquals(testFixture.dokumenter, innsending.dokumenter)
            assertEquals(testFixture.innsendingType, innsending.innsendingType)
        }
        testRapid.inspektør.size
        assertEquals(1, testRapid.inspektør.size)
        assertNotNull(
            testRapid.inspektør.field(
                index = 0,
                field = "@løsning",
            ).get(testFixture.behov[0].name),
        )
    }

    @ParameterizedTest
    @MethodSource("testFixtures")
    fun `Skal ikke håndtere NyInnsending hendelse med løsning`(testFixture: TestFixture) {
        NyEttersendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator,
        )

        testRapid.sendTestMessage(
            lagTestJson(
                testFixture.copy(
                    løsning = mapOf(
                        "@løsning" to mapOf(
                            "innsendingId" to UUID.randomUUID().toString(),
                        ),
                    ),
                ),
            ),
        )

        verify(exactly = 0) { mediator.behandleNyInnsendingHendelse(any<NyInnsendingHendelse>()) }
    }

    @ParameterizedTest
    @MethodSource("testFixtures")
    fun `Skal håndtere NyInnsending uten dokumenter`(testFixture: TestFixture) {
        NyEttersendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator,
        )

        testRapid.sendTestMessage(lagTestJson(testFixture.copy(dokumenter = emptyList())))
        verify(exactly = 1) { mediator.behandleNyInnsendingHendelse(any<NyInnsendingHendelse>()) }
        TestInnsendingVisitor(slot.captured.innsending).let { innsending ->
            assertEquals(testFixture.ident, innsending.ident)
            assertEquals(testFixture.søknadId, innsending.søknadId)
            assertEquals(testFixture.innsendtTidspunkt, innsending.innsendtTidspunkt.toString())
            assertEquals(testFixture.innsendingType, innsending.innsendingType)
            assertEquals(listOf<Innsending.Dokument>(), innsending.dokumenter)
        }
    }
}
