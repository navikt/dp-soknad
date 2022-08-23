package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

internal class SøknadPostgresRepositoryTest {
    private val søknadId = UUID.randomUUID()

    private val dokumentFaktum =
        Faktum(faktumJson("1", "f1"))
    private val faktaSomSannsynliggjøres =
        mutableSetOf(
            Faktum(faktumJson("2", "f2"))
        )
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val krav = Krav(
        sannsynliggjøring
    )

    val ident = "12345678910"
    private val originalPerson = Person(ident) {

        mutableListOf(
            Søknad.rehydrer(
                søknadId = søknadId,
                person = it,
                tilstandsType = "Journalført",
                dokument = Søknad.Dokument(
                    varianter = listOf(
                        Søknad.Dokument.Variant(
                            urn = "urn:soknad:fil1",
                            format = "ARKIV",
                            type = "PDF"
                        ),
                        Søknad.Dokument.Variant(
                            urn = "urn:soknad:fil2",
                            format = "ARKIV",
                            type = "PDF"
                        )
                    )
                ),
                journalpostId = "journalpostid",
                innsendtTidspunkt = ZonedDateTime.now(),
                språk = Språk("NO"),
                dokumentkrav = Dokumentkrav.rehydrer(
                    krav = setOf(krav)
                )
            )
        )
    }

    @Test
    fun hentDokumentkrav() {
        withMigratedDb {
            LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).lagre(originalPerson)
            val søknadPostgresRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource)
            assertNotNull(søknadPostgresRepository.hentDokumentkravFor(søknadId, ident))
        }
    }
}
