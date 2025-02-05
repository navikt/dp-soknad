package no.nav.dagpenger.soknad.innsending.meldinger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.InnsendingType.NY_DIALOG
import no.nav.dagpenger.soknad.InnsendingVisitor
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.tjenester.NyInnsendingBehovMottak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal class NyInnsendingBehovMottakTest {
    companion object {
        @JvmStatic
        fun testFixtures(): List<TestFixture> =
            listOf(
                TestFixture(
                    behov = listOf(Behovtype.NyInnsending),
                    innsendingType = NY_DIALOG,
                ),
                TestFixture(
                    behov = listOf(Behovtype.NyInnsending, Behovtype.NySøknad),
                    innsendingType = NY_DIALOG,
                ),
            )
    }

    private val testRapid = TestRapid()
    private val slot = slot<NyInnsendingHendelse>()
    private val mediator =
        mockk<InnsendingMediator>().also {
            every { it.behandle(capture(slot)) } just Runs
        }

    @BeforeEach
    fun setup() = testRapid.reset()

    @ParameterizedTest
    @MethodSource("testFixtures")
    fun `Skal håndtere NyInnsending hendelse`(testFixture: TestFixture) {
        NyInnsendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator,
        )

        testRapid.sendTestMessage(
            lagTestJson(fixture = testFixture),
        )

        verify(exactly = 1) { mediator.behandle(any<NyInnsendingHendelse>()) }
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
            testRapid.inspektør
                .field(
                    index = 0,
                    field = "@løsning",
                ).get(testFixture.behov[0].name),
        )
    }

    @ParameterizedTest
    @MethodSource("testFixtures")
    fun `Skal ikke håndtere NyInnsending hendelse med løsning`(testFixture: TestFixture) {
        NyInnsendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator,
        )

        testRapid.sendTestMessage(
            lagTestJson(
                testFixture.copy(
                    løsning =
                    mapOf(
                        "@løsning" to
                            mapOf(
                                "innsendingId" to UUID.randomUUID().toString(),
                            ),
                    ),
                ),
            ),
        )

        verify(exactly = 0) { mediator.behandle(any<NyInnsendingHendelse>()) }
    }

    @ParameterizedTest
    @MethodSource("testFixtures")
    fun `Skal håndtere NyInnsending uten dokumenter`(testFixture: TestFixture) {
        NyInnsendingBehovMottak(
            rapidsConnection = testRapid,
            mediator = mediator,
        )

        testRapid.sendTestMessage(lagTestJson(testFixture.copy(dokumenter = emptyList())))
        verify(exactly = 1) { mediator.behandle(any<NyInnsendingHendelse>()) }
        TestInnsendingVisitor(slot.captured.innsending).let { innsending ->
            assertEquals(testFixture.ident, innsending.ident)
            assertEquals(testFixture.søknadId, innsending.søknadId)
            assertEquals(testFixture.innsendtTidspunkt, innsending.innsendtTidspunkt.toString())
            assertEquals(testFixture.innsendingType, innsending.innsendingType)
            assertEquals(listOf<Innsending.Dokument>(), innsending.dokumenter)
        }
    }
}

internal fun lagTestJson(fixture: TestFixture): String {
    val map =
        mutableMapOf(
            "@event_name" to "behov",
            "@behov" to fixture.behov,
            "søknad_uuid" to fixture.søknadId,
            "innsendtTidspunkt" to fixture.innsendtTidspunkt,
            "dokumentkrav" to fixture.dokumenter,
            "ident" to fixture.ident,
        ).also { mutableMap ->
            fixture.løsning?.let {
                mutableMap["@løsning"] = it
            }
        }
    return JsonMessage.newMessage(map).toJson()
}

internal class TestInnsendingVisitor(
    innsending: Innsending,
) : InnsendingVisitor {
    lateinit var søknadId: UUID
    lateinit var innsendtTidspunkt: ZonedDateTime
    lateinit var ident: String
    lateinit var dokumenter: List<Innsending.Dokument>
    lateinit var innsendingType: InnsendingType

    init {
        innsending.accept(this)
    }

    override fun visit(
        innsendingId: UUID,
        søknadId: UUID,
        ident: String,
        innsendingType: InnsendingType,
        tilstand: Innsending.TilstandType,
        sistEndretTilstand: LocalDateTime,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        dokumenter: List<Innsending.Dokument>,
        metadata: Innsending.Metadata?,
    ) {
        this.søknadId = søknadId
        this.innsendtTidspunkt = innsendt
        this.ident = ident
        this.dokumenter = dokumenter
        this.innsendingType = innsendingType
    }
}

internal data class TestFixture(
    val søknadId: UUID = UUID.randomUUID(),
    val innsendtTidspunkt: String = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).toString(),
    val dokumenter: List<Innsending.Dokument> =
        listOf(
            Innsending.Dokument(
                uuid = UUID.randomUUID(),
                kravId = "k1",
                skjemakode = "s1",
                varianter =
                listOf(
                    Innsending.Dokument.Dokumentvariant(
                        uuid = UUID.randomUUID(),
                        filnavn = "f1",
                        urn = "urn:vedlegg:f1",
                        variant = "n1",
                        type = "t1",
                    ),
                    Innsending.Dokument.Dokumentvariant(
                        uuid = UUID.randomUUID(),
                        filnavn = "f2",
                        urn = "urn:vedlegg:f2",
                        variant = "n2",
                        type = "t2",
                    ),
                ),
            ),
            Innsending.Dokument(
                uuid = UUID.randomUUID(),
                kravId = null,
                skjemakode = null,
                varianter = listOf(),
            ),
        ),
    val ident: String = "1234",
    val løsning: Map<String, Any>? = null,
    val behov: List<Behovtype>,
    val innsendingType: InnsendingType,
)
