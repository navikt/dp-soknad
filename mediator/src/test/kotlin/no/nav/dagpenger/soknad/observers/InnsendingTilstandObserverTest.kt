package no.nav.dagpenger.soknad.observers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.equals.shouldBeEqual
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.InnsendingObserver
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import java.util.UUID
import kotlin.test.Test

internal class InnsendingTilstandObserverTest {
    private val testRapid = TestRapid()
    private val innsendingTilstandObserver = InnsendingTilstandObserver(testRapid)

    @Test
    fun `publiserer tilstandsendringer på rapid`() {
        val forventetFerdig = java.time.LocalDateTime.now()
        val innsendingId = UUID.randomUUID()
        innsendingTilstandObserver.innsendingTilstandEndret(
            InnsendingObserver.InnsendingEndretTilstandEvent(
                ident = "12345678901",
                innsendingId = innsendingId,
                innsendingType = Innsending.InnsendingType.NY_DIALOG,
                gjeldendeTilstand = Innsending.TilstandType.Opprettet,
                forrigeTilstand = Innsending.TilstandType.AvventerArkiverbarSøknad,
                forventetFerdig = forventetFerdig,
            ),
        )

        assertSoftly {
            testRapid.inspektør.size shouldBeEqual 1
            testRapid.inspektør.key(0).toString() shouldBeEqual "12345678901"
            with(testRapid.inspektør.message(0)) {
                this["@event_name"].asText() shouldBeEqual "innsending_tilstand_endret"
                this["innsendingId"].asUUID() shouldBeEqual innsendingId
                this["innsendingType"].asText() shouldBeEqual "NY_DIALOG"
                this["gjeldendeTilstand"].asText() shouldBeEqual "Opprettet"
                this["forrigeTilstand"].asText() shouldBeEqual "AvventerArkiverbarSøknad"
                this["forventetFerdig"].asLocalDateTime() shouldBeEqual forventetFerdig
            }
        }
    }
}
