package no.nav.dagpenger.soknad

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

private val namespace = "dp_soknad"

object Metrics {
    val onBehandleHendelse: Histogram = Histogram.build()
        .namespace(namespace)
        .name("behandle_hendelse")
        .help("Hvor lang det tar å behandle en hendelse")
        .labelNames("hendelse_navn", "fase")
        .register()
    val onFaktumSvar: Histogram = Histogram.build()
        .namespace(namespace)
        .name("faktum_svar")
        .help("Hvor lang det å håndtere svar på faktum")
        .labelNames("fase")
        .register()
    val søknadDataRequests: Counter = Counter.build()
        .namespace(namespace)
        .name("soknad_data_requests")
        .help("Hvor lang tid det tar å hente søknaddata")
        .register()
    val søknadDataRetries: Counter = Counter.build()
        .namespace(namespace)
        .name("soknad_data_retries")
        .help("Hvor mange forsøk det tar å hente søknaddata")
        .labelNames("attempt")
        .register()
    val søknadDataTimeouts: Counter = Counter.build()
        .namespace(namespace)
        .name("soknad_data_timeouts")
        .help("Hvor mange ganger vi ikke klarer å hente søknaddata før timeout")
        .register()
    val insertAktivitetslogg: Histogram = Histogram.build()
        .namespace(namespace)
        .name("insert_aktivitetslogg")
        .help("Hvor lang tid det tar å lagre aktivitetsloggen")
        .register()
    val insertAktivitetsloggSize: Histogram = Histogram.build()
        .namespace(namespace)
        .name("insert_aktivitetslogg_size")
        .help("Hvor mange rader har aktivitetsloggen")
        .register()
    val søknadTilstandTeller = Counter
        .build()
        .namespace(namespace)
        .name("soknad_tilstand_teller")
        .help("Teller tilstandsendringer på søknad")
        .labelNames("tilstand", "forrigetilstand")
        .register()

    val søknadInnsendingTilstandTeller = Counter
        .build()
        .namespace(namespace)
        .name("innsending_tilstand_teller")
        .help("Teller tilstandsendringer på innsendinger ")
        .labelNames("type", "tilstand", "forrigetilstand")
        .register()
}
