package no.nav.dagpenger.soknad.db

import FerdigSøknadData
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

internal class PostgresDokumentkravRepositoryTest {

    @Test
    fun `Kan opprette og slette filer`() {
        val id = UUID.randomUUID()

        setup(lagSøknad(søknadId = id, ident = "123", "1")) { dokumentKravRepository ->
            val fil1 = Krav.Fil(
                filnavn = "fil1",
                urn = URN.rfc8141().parse("urn:vedlegg:$id/fil1"),
                storrelse = 0,
                tidspunkt = ZonedDateTime.now(),
                bundlet = false
            )
            val fil2 = fil1.copy(urn = URN.rfc8141().parse("urn:vedlegg:$id/fil2"))
            dokumentKravRepository.håndter(
                LeggTilFil(
                    søknadID = id,
                    ident = "123",
                    kravId = "1",
                    fil = fil1
                )
            )

            dokumentKravRepository.håndter(
                LeggTilFil(
                    søknadID = id,
                    ident = "123",
                    kravId = "1",
                    fil = fil2
                )
            )

            dokumentKravRepository.hent(id).let { dokumentKrav ->
                assertEquals(dokumentKrav.aktiveDokumentKrav().size, 1)
                assertEquals(setOf(fil1, fil2), dokumentKrav.aktiveDokumentKrav().single().svar.filer)
            }

            dokumentKravRepository.håndter(
                SlettFil(
                    søknadID = id,
                    ident = "123",
                    kravId = "1",
                    urn = fil1.urn
                )
            )

            dokumentKravRepository.hent(id).let { dokumentKrav ->
                assertEquals(dokumentKrav.aktiveDokumentKrav().size, 1)
                assertEquals(setOf(fil2), dokumentKrav.aktiveDokumentKrav().single().svar.filer)
            }
        }
    }

    private fun setup(søknad: Søknad, block: (repository: PostgresDokumentkravRepository) -> Unit) {
        withMigratedDb {
            SøknadPostgresRepository(dataSource = PostgresDataSourceBuilder.dataSource).lagre(søknad)
            block(PostgresDokumentkravRepository(PostgresDataSourceBuilder.dataSource))
        }
    }

    private fun lagSøknad(
        søknadId: UUID = UUID.randomUUID(),
        ident: String = "1234",
        vararg kravId: String
    ): Søknad {
        val now = ZonedDateTime.now()
        return Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = now,
            innsendt = now,
            språk = Språk(verdi = "NO"),
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = kravId.map {
                    Sannsynliggjøring(
                        id = it,
                        faktum = Faktum(
                            json = faktumJson(
                                id = it,
                                beskrivendeId = "f$it",
                            )
                        )
                    )
                }.map {
                    Krav(it)
                }.toSet()
            ),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = Prosessversjon("Dagpenger", 1),
            data = FerdigSøknadData
        )
    }
}
