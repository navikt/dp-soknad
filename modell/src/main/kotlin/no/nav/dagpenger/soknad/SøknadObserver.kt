package no.nav.dagpenger.soknad

import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

interface DokumentkravObserver {
    fun dokumentkravInnsendt(event: DokumentkravInnsendtEvent) {}
    data class DokumentkravInnsendtEvent(
        val søknadId: UUID,
        val ident: String,
        var søknadType: String? = null,
        var innsendingstype: String? = null,
        var innsendttidspunkt: LocalDateTime,
        val ferdigBesvart: Boolean,
        val dokumentkrav: List<DokumentkravInnsendt>,
    ) {
        val hendelseId: UUID = UUID.randomUUID()

        data class DokumentkravInnsendt(
            val dokumentnavn: String,
            val skjemakode: String,
            val valg: String,
        )
    }
}

interface SøknadObserver : DokumentkravObserver {
    fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {}
    fun innsendingTilstandEndret(event: SøknadInnsendingEndretTilstandEvent) {}

    fun søknadSlettet(event: SøknadSlettetEvent) {}
    fun søknadMigrert(event: SøknadMigrertEvent) {}

    fun søknadInnsendt(event: SøknadInnsendtEvent) {}

    data class SøknadInnsendtEvent(
        val søknadId: UUID,
        val søknadTidspunkt: ZonedDateTime,
        val søknadData: String,
        val ident: String,
    )

    data class SøknadEndretTilstandEvent(
        val søknadId: UUID,
        val ident: String,
        val prosessversjon: Prosessversjon?,
        val gjeldendeTilstand: Søknad.Tilstand.Type,
        val forrigeTilstand: Søknad.Tilstand.Type,
    )

    data class SøknadInnsendingEndretTilstandEvent(
        val søknadId: UUID,
        val innsending: InnsendingObserver.InnsendingEndretTilstandEvent,
    )

    data class SøknadSlettetEvent(
        val søknadId: UUID,
        val ident: String,
    )

    data class SøknadMigrertEvent(
        val søknkadId: UUID,
        val ident: String,
        val forrigeProsessversjon: Prosessversjon,
        val gjeldendeProsessversjon: Prosessversjon,
    )
}

// internal class SøknadInnsendtObserver(private val rapidsConnection: RapidsConnection) : SøknadObserver {
//    override fun søknadInnsendt(event: SøknadObserver.SøknadInnsendtEvent) {
//        rapidsConnection.publish(event.toMeldingOmNySøknad().asMessage().toJson())
//    }
//
//    private fun SøknadObserver.SøknadInnsendtEvent.toMeldingOmNySøknad() {
//        return JsonMessage.newMessage(
//            eventName = "søknad_innsendt",
//            map =
//            mapOf(
//                "søknad_uuid" to søknadId,
//                "søknadstidspunkt" to innsendt,
//                "søknadData" to søknadData,
//                "ident" to ident,
//            ),
//        )
//    }
// }
