package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.PersonObserver.SøknadEndretTilstandEvent
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

    private val sannsynliggjøringer: MutableMap<UUID, Set<Sannsynliggjøring>> = mutableMapOf()
    private val dokumentkrav: MutableMap<UUID, Set<Dokumentkrav>> = mutableMapOf()

    fun sannsynliggjøringer(søknadId: UUID): Set<Sannsynliggjøring> =
        sannsynliggjøringer[søknadId] ?: throw AssertionError("Fant ikke sannsynliggjøringer fra søknad $søknadId")

    fun dokumentkrav(søknadId: UUID): Set<Dokumentkrav> = dokumentkrav[søknadId] ?: throw AssertionError("Fant ikke dokumentkrav fra søknad $søknadId")

    init {
        person.accept(this)
    }

    override fun visitSannsynliggjøring(søknadId: UUID, sannsynliggjøring: Set<Sannsynliggjøring>) {
        this.sannsynliggjøringer[søknadId] = sannsynliggjøring
    }

    override fun visitDokumentkrav(søknadId: UUID, dokumentkrav: Set<Dokumentkrav>) {
        this.dokumentkrav[søknadId] = dokumentkrav
    }
}
