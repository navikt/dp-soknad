package no.nav.dagpenger.søknad

import io.prometheus.client.Histogram

object Metrics {
    val onBehandleHendelse: Histogram = Histogram.build()
        .namespace("dp_soknad")
        .name("behandle_hendelse")
        .help("Hvor lang det tar å behandle en hendelse")
        .labelNames("hendelse_navn", "fase")
        .register()
    val onFaktumSvar: Histogram = Histogram.build()
        .namespace("dp_soknad")
        .name("faktum_svar")
        .help("Hvor lang det å håndtere svar på faktum")
        .labelNames("fase")
        .register()

    val insertAktivitetslogg: Histogram = Histogram.build()
        .namespace("dp_soknad")
        .name("insert_aktivitetslogg")
        .help("Hvor lang tid det tar å lagre aktivitetsloggen")
        .register()
    val insertAktivitetsloggSize: Histogram = Histogram.build()
        .namespace("dp_soknad")
        .name("insert_aktivitetslogg_size")
        .help("Hvor mange rader har aktivitetsloggen")
        .register()
}
