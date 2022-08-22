package no.nav.dagpenger.soknad.db

import com.zaxxer.hikari.HikariDataSource
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import java.util.UUID

class SøknadPostgresRepository(private val dataSource: HikariDataSource) :
    SøknadRepository {
    override fun hentDokumentkravFor(søknadId: UUID, ident: String): Dokumentkrav {
        TODO("venter..")

        // using(sessionOf(dataSource)) { session ->
        //     session.run(
        //         // language=PostgreSQL
        //         queryOf("""
        //             SELECT
        //         """.trimIndent()),
        //         mapOf()
        //     )
        //
        //
        // }
    }
}
