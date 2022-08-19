package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.PersonObserver.SøknadEndretTilstandEvent
import java.time.ZonedDateTime
import java.util.UUID

class TestPersonObserver : PersonObserver {

    internal var slettet: Boolean = false
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>().also {
        it.add(Søknad.Tilstand.Type.UnderOpprettelse)
    }

    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {
        tilstander.add(event.gjeldendeTilstand)
    }

    override fun søknadSlettet(event: PersonObserver.SøknadSlettetEvent) {
        slettet = true
    }
}

class TestPersonVisitor(person: Person) : PersonVisitor {

    private val sannsynliggjøringer: MutableSet<Sannsynliggjøring> = mutableSetOf()
    private val aktiveKrav: MutableSet<Krav> = mutableSetOf()
    private val inaktiveKrav: MutableSet<Krav> = mutableSetOf()

    init {
        person.accept(this)
    }

    fun sannsynliggjøringer(): Set<Sannsynliggjøring> = sannsynliggjøringer.toSet()

    fun aktiveDokumentkrav(): Set<Krav> = aktiveKrav.toSet()
    fun inaktiveDokumentkrav(): Set<Krav> = inaktiveKrav.toSet()

    override fun visitSøknad(
        søknadId: UUID,
        person: Person,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav
    ) {
        this.sannsynliggjøringer.addAll(dokumentkrav.sannsynliggjøringer())
        this.aktiveKrav.addAll(dokumentkrav.aktiveDokumentKrav())
        this.inaktiveKrav.addAll(dokumentkrav.inAktiveDokumentKrav())
    }
}
