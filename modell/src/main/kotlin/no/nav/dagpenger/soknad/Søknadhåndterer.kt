package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Companion.finnSøknad
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNyInnsendingHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse

class Søknadhåndterer private constructor(
    søknadsfunksjon: (søknadhåndterer: Søknadhåndterer) -> MutableList<Søknad>,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : SøknadObserver {

    companion object {
        internal fun rehydrer(
            aktivitetslogg: Aktivitetslogg,
            søknadsfunksjon: (søknadhåndterer: Søknadhåndterer) -> MutableList<Søknad>,
        ): Søknadhåndterer = Søknadhåndterer(søknadsfunksjon, aktivitetslogg)
    }

    // Navneforslag: Dialog? --> Må kunne finne ut av type
    private val søknader: MutableList<Søknad>

    init {
        this.søknader = søknadsfunksjon(this)
    }

    private val observers = mutableListOf<SøknadObserver>()

    constructor() : this({ mutableListOf() })
    constructor(søknadsfunksjon: (søknadhåndterer: Søknadhåndterer) -> MutableList<Søknad>) : this(
        søknadsfunksjon = søknadsfunksjon, aktivitetslogg = Aktivitetslogg()
    )

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        // if (søknader.harAlleredeOpprettetSøknad()) {
        //    ønskeOmNySøknadHendelse.severe("Kan ikke ha flere enn én opprettet søknad.")
        // }
        kontekst(ønskeOmNySøknadHendelse, "Ønske om søknad registrert")
        søknader.add(
            Søknad(
                ønskeOmNySøknadHendelse.søknadID(),
                ønskeOmNySøknadHendelse.språk(),
                this,
                ønskeOmNySøknadHendelse.ident()
            ).also {
                it.håndter(ønskeOmNySøknadHendelse)
            }
        )
    }

    fun håndter(ønskeOmNyInnsendingHendelse: ØnskeOmNyInnsendingHendelse) {
        kontekst(ønskeOmNyInnsendingHendelse, "Ønske om innsending registrert")
        søknader.add(
            Søknad(
                ønskeOmNyInnsendingHendelse.søknadID(),
                ønskeOmNyInnsendingHendelse.språk(),
                this,
                ønskeOmNyInnsendingHendelse.ident()
            ).also {
                it.håndter(ønskeOmNyInnsendingHendelse)
            }
        )
    }

    fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse) {
        kontekst(harPåbegyntSøknadHendelse, "Fortsetter på påbegynt søknad")
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse, "Oppretter søknad")

        val søknaden = finnSøknad(søknadOpprettetHendelse)

        søknaden.håndter(søknadOpprettetHendelse)
    }

    fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse) {
        kontekst(faktumOppdatertHendelse, "Faktum oppdatert")
        finnSøknad(faktumOppdatertHendelse).also { søknaden ->
            søknaden.håndter(faktumOppdatertHendelse)
        }
    }

    fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse) {
        kontekst(søkeroppgaveHendelse, "Søkeroppgave mottatt")
        finnSøknad(søkeroppgaveHendelse).also { søknaden ->
            søknaden.håndter(søkeroppgaveHendelse)
        }
    }

    fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        kontekst(søknadInnsendtHendelse, "Sender inn søknaden")
        val søknaden = finnSøknad(søknadInnsendtHendelse)
        søknaden.håndter(søknadInnsendtHendelse)
    }

    fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse) {
        kontekst(arkiverbarSøknadMotattHendelse, "Arkiverbar søknad motatt")
        finnSøknad(arkiverbarSøknadMotattHendelse).also { søknaden ->
            søknaden.håndter(arkiverbarSøknadMotattHendelse)
        }
    }

    fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(søknadMidlertidigJournalførtHendelse, "Søknad midlertidig journalført")
        finnSøknad(søknadMidlertidigJournalførtHendelse).also { søknaden ->
            søknaden.håndter(søknadMidlertidigJournalførtHendelse)
        }
    }

    fun håndter(journalførtHendelse: JournalførtHendelse) {
        kontekst(journalførtHendelse, "Søknad journalført")
        val søknaden = finnSøknad(journalførtHendelse)
        when {
            søknaden != null -> søknaden.håndter(journalførtHendelse)
            else -> journalførtHendelse.info("Fant ikke søknaden for ${journalførtHendelse.journalpostId()}")
        }
    }

    fun håndter(slettSøknadHendelse: SlettSøknadHendelse) {
        kontekst(slettSøknadHendelse, "Forsøker å slette søknad med ${slettSøknadHendelse.søknadID}")
        finnSøknad(slettSøknadHendelse).also { søknaden ->
            søknaden.håndter(slettSøknadHendelse)
        }
    }

    fun accept(visitor: SøknadhåndtererVisitor) {
        visitor.visitSøknader(søknader)
        visitor.preVisitSøknader()
        søknader.forEach { it.accept(visitor) }
        visitor.postVisitSøknader()
        aktivitetslogg.accept(visitor)
    }

    fun addObserver(søknadObserver: SøknadObserver) {
        observers.add(søknadObserver)
    }

    override fun søknadTilstandEndret(event: SøknadObserver.SøknadEndretTilstandEvent) {
        observers.forEach {
            it.søknadTilstandEndret(event)
        }
    }

    override fun søknadSlettet(event: SøknadObserver.SøknadSlettetEvent) {
        observers.forEach {
            it.søknadSlettet(event)
        }
    }

    private fun finnSøknad(søknadHendelse: SøknadHendelse) =
        søknader.finnSøknad(søknadHendelse.søknadID()) ?: søknadHendelse.severe("Fant ikke søknaden")

    private fun finnSøknad(journalførtHendelse: JournalførtHendelse): Søknad? {
        return søknader.finnSøknad(journalførtHendelse.journalpostId())
    }

    private fun kontekst(hendelse: Hendelse, melding: String) {
        hendelse.kontekst(Personkontekst(hendelse.ident(), aktivitetslogg))
        hendelse.info(melding)
    }
}

class Personkontekst(val ident: String, internal val aktivitetslogg: Aktivitetslogg) : Aktivitetskontekst {
    override fun toSpesifikkKontekst(): SpesifikkKontekst =
        SpesifikkKontekst(kontekstType = "person", mapOf("ident" to ident))
}
