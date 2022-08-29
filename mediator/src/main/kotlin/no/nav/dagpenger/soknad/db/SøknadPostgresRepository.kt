package no.nav.dagpenger.soknad.db

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.hentDokumentKrav
import no.nav.dagpenger.soknad.livssyklus.insertKravData
import no.nav.dagpenger.soknad.livssyklus.toDokumentKravData
import java.util.UUID

class SøknadPostgresRepository(private val dataSource: HikariDataSource) :
    SøknadRepository {
    override fun hentDokumentkravFor(søknadId: UUID, ident: String): Dokumentkrav {
        if (harTilgang(ident, søknadId)) {
            return using(sessionOf(dataSource)) { session ->
                Dokumentkrav.rehydrer(session.hentDokumentKrav(søknadId).map { it.rehydrer() }.toSet())
            }
        } else {
            throw IkkeTilgangExeption("Har ikke tilgang til søknadId $søknadId")
        }
    }

    override fun harTilgang(ident: String, søknadId: UUID): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                // language=PostgreSQL
                queryOf(
                    """
                       SELECT 1 from soknad_v1 WHERE person_ident = :ident AND uuid = :uuid
                    """.trimIndent(),
                    mapOf(
                        "uuid" to søknadId.toString(),
                        "ident" to ident
                    )
                ).map {
                    it.int(1) == 1
                }.asSingle
            ) ?: false
        }

    override fun oppdaterDokumentkrav(søknadId: UUID, dokumentkrav: Dokumentkrav) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                dokumentkrav.toDokumentKravData().kravData.insertKravData(søknadId, transactionalSession)
            }
        }
    }
}
