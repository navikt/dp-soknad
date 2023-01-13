package no.nav.dagpenger.soknad

import SøknadMetrikkObserver
import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.soknad.data.søknadData
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.InnsendingPostgresRepository
import no.nav.dagpenger.soknad.innsending.tjenester.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.innsending.tjenester.JournalførtMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyEttersendingBehovMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyInnsendingBehovMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyJournalpostMottak
import no.nav.dagpenger.soknad.innsending.tjenester.SkjemakodeMottak
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.FerdigstiltSøknadPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.ferdigStiltSøknadRouteBuilder
import no.nav.dagpenger.soknad.livssyklus.påbegynt.MigrertSøknadMottak
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgaveMottak
import no.nav.dagpenger.soknad.livssyklus.start.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.mal.SøknadsMalMottak
import no.nav.dagpenger.soknad.observers.SøknadLoggerObserver
import no.nav.dagpenger.soknad.observers.SøknadTilstandObserver
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.soknad.sletterutine.SlettSøknaderJob
import no.nav.dagpenger.soknad.sletterutine.UtdaterteSøknaderJob
import no.nav.dagpenger.soknad.status.BehandlingsstatusHttpClient
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.runMigration
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(config: Map<String, String>) : RapidsConnection.StatusListener {

    private val rapidsConnection: RapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(config)
    ).withKtorModule {
        api(
            personaliaRouteBuilder = personaliaRouteBuilder(
                personOppslag = PersonOppslag(createPersonOppslag(Configuration.pdlUrl)),
                kontonummerOppslag = KontonummerOppslag(
                    kontoRegisterUrl = Configuration.kontoRegisterUrl,
                    tokenProvider = Configuration.tokenXClient(Configuration.kontoRegisterScope)
                )
            ),
            søknadRouteBuilder = søknadApiRouteBuilder(
                søknadMediator(),
                BehandlingsstatusHttpClient()
            ),
            ferdigstiltRouteBuilder = ferdigStiltSøknadRouteBuilder(
                ferdigstiltRepository
            ),
            søknadDataRouteBuilder = søknadData(mediator = søknadMediator)
        )
    }.build()

    private val søknadMalRepository = SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource)
    private val ferdigstiltRepository = FerdigstiltSøknadPostgresRepository(
        PostgresDataSourceBuilder.dataSource
    )
    private val søknadRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).also {
        SøknadMigrering(it, søknadMalRepository, rapidsConnection)
    }
    private val søknadMediator = SøknadMediator(
        rapidsConnection = rapidsConnection,
        søknadDataRepository = SøknadDataPostgresRepository(PostgresDataSourceBuilder.dataSource),
        søknadMalRepository = søknadMalRepository,
        ferdigstiltSøknadRepository = ferdigstiltRepository,
        søknadRepository = søknadRepository,
        søknadObservers = listOf(
            SøknadLoggerObserver,
            SøknadMetrikkObserver,
            SøknadTilstandObserver(rapidsConnection)
        )
    ).also {
        SøknadOpprettetHendelseMottak(rapidsConnection, it)
        SøkerOppgaveMottak(rapidsConnection, it)
        MigrertSøknadMottak(rapidsConnection, it)
    }

    private val innsendingMediator = InnsendingMediator(
        rapidsConnection = rapidsConnection,
        innsendingRepository = InnsendingPostgresRepository(PostgresDataSourceBuilder.dataSource)
    ).also {
        ArkiverbarSøknadMottattHendelseMottak(rapidsConnection, it)
        NyJournalpostMottak(rapidsConnection, it)
        JournalførtMottak(rapidsConnection, it)
        SkjemakodeMottak(rapidsConnection, it)
        NyInnsendingBehovMottak(rapidsConnection, it)
        NyEttersendingBehovMottak(rapidsConnection, it)
    }

    init {
        rapidsConnection.register(this)
    }

    fun start() {
        rapidsConnection.start()
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
        SøknadsMalMottak(rapidsConnection, søknadMalRepository)
        UtdaterteSøknaderJob.sletterutine(søknadMediator)
        SlettSøknaderJob.sletterutine(søknadMediator)
    }

    private fun søknadMediator(): SøknadMediator = søknadMediator
}
