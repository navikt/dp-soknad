package no.nav.dagpenger.soknad

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.soknad.arbeidsforhold.AaregClient
import no.nav.dagpenger.soknad.arbeidsforhold.ArbeidsforholdOppslag
import no.nav.dagpenger.soknad.arbeidsforhold.EregClient
import no.nav.dagpenger.soknad.arbeidsforhold.arbeidsforholdRouteBuilder
import no.nav.dagpenger.soknad.data.søknadData
import no.nav.dagpenger.soknad.db.PostgresDokumentkravRepository
import no.nav.dagpenger.soknad.db.SøknadDataPostgresRepository
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.InnsendingPostgresRepository
import no.nav.dagpenger.soknad.innsending.tjenester.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.innsending.tjenester.JournalførtMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyEttersendingBehovMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyInnsendingBehovMottak
import no.nav.dagpenger.soknad.innsending.tjenester.NyJournalpostMottak
import no.nav.dagpenger.soknad.innsending.tjenester.PåminnelseJobb
import no.nav.dagpenger.soknad.innsending.tjenester.SkjemakodeMottak
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.FerdigstiltSøknadPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.ferdigStiltSøknadRouteBuilder
import no.nav.dagpenger.soknad.livssyklus.ferdigstilt.SøknadInnsendtTidspunktTjeneste
import no.nav.dagpenger.soknad.livssyklus.påbegynt.MigrertSøknadMottak
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgaveMottak
import no.nav.dagpenger.soknad.livssyklus.start.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.mal.SøknadsMalMottak
import no.nav.dagpenger.soknad.monitoring.InnsendingMetrikkObserver
import no.nav.dagpenger.soknad.monitoring.SøknadMetrikkObserver
import no.nav.dagpenger.soknad.observers.SøknadInnsendtObserver
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

internal class ApplicationBuilder(
    config: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val rapidsConnection: RapidsConnection =
        RapidApplication
            .create(
                config,
            ) { engine, _ ->
                engine.application.api(
                    personaliaRouteBuilder =
                    personaliaRouteBuilder(
                        personOppslag = PersonOppslag(createPersonOppslag(Configuration.pdlUrl)),
                        kontonummerOppslag =
                        KontonummerOppslag(
                            kontoRegisterUrl = Configuration.kontoRegisterUrl,
                            tokenProvider = Configuration.tokenXClient(Configuration.kontoRegisterScope),
                        ),
                    ),
                    arbeidsforholdRouteBuilder =
                    arbeidsforholdRouteBuilder(
                        arbeidsforholdOppslag =
                        ArbeidsforholdOppslag(
                            aaregClient =
                            AaregClient(
                                aaregUrl = Configuration.aaregUrl,
                                tokenProvider = Configuration.tokenXClient(Configuration.aaregAudience),
                            ),
                            eregClient =
                            EregClient(
                                eregUrl = Configuration.eregUrl,
                            ),
                        ),
                    ),
                    søknadRouteBuilder =
                    søknadApiRouteBuilder(
                        søknadMediator(),
                        BehandlingsstatusHttpClient(),
                    ),
                    ferdigstiltRouteBuilder =
                    ferdigStiltSøknadRouteBuilder(
                        ferdigstiltRepository,
                    ),
                    søknadDataRouteBuilder = søknadData(mediator = søknadMediator),
                )
            }

    private val søknadMalRepository = SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource)
    private val ferdigstiltRepository =
        FerdigstiltSøknadPostgresRepository(
            PostgresDataSourceBuilder.dataSource,
        )
    private val søknadRepository =
        SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).also {
            SøknadMigrering(it, søknadMalRepository, rapidsConnection)
        }

    private val dokumentkravRepository = PostgresDokumentkravRepository(PostgresDataSourceBuilder.dataSource)

    private val søknadMediator =
        SøknadMediator(
            rapidsConnection = rapidsConnection,
            søknadDataRepository = SøknadDataPostgresRepository(PostgresDataSourceBuilder.dataSource),
            søknadMalRepository = søknadMalRepository,
            ferdigstiltSøknadRepository = ferdigstiltRepository,
            søknadRepository = søknadRepository,
            dokumentkravRepository = dokumentkravRepository,
            søknadObservers =
            listOf(
                SøknadLoggerObserver,
                SøknadMetrikkObserver,
                SøknadTilstandObserver(rapidsConnection),
                SøknadInnsendtObserver(rapidsConnection),
            ),
        ).also {
            SøknadOpprettetHendelseMottak(rapidsConnection, it)
            SøkerOppgaveMottak(rapidsConnection, it)
            MigrertSøknadMottak(rapidsConnection, it)
            SøknadInnsendtTidspunktTjeneste(rapidsConnection, it)
        }

    private val innsendingMediator =
        InnsendingMediator(
            rapidsConnection = rapidsConnection,
            innsendingRepository = InnsendingPostgresRepository(PostgresDataSourceBuilder.dataSource),
            innsendingObservers =
            listOf(
                InnsendingMetrikkObserver,
            ),
        ).also {
            ArkiverbarSøknadMottattHendelseMottak(rapidsConnection, it)
            NyJournalpostMottak(rapidsConnection, it)
            JournalførtMottak(rapidsConnection, it)
            SkjemakodeMottak(rapidsConnection, it)
            NyInnsendingBehovMottak(rapidsConnection, it)
            NyEttersendingBehovMottak(rapidsConnection, it)
        }

    val påminnelseJobb = PåminnelseJobb(innsendingMediator, PostgresDataSourceBuilder.dataSource)

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
