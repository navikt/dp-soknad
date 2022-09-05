package no.nav.dagpenger.soknad.db

import com.zaxxer.hikari.HikariDataSource
import de.slub.urn.URN
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.hentDokumentKrav
import no.nav.dagpenger.soknad.livssyklus.insertKravData
import no.nav.dagpenger.soknad.livssyklus.toDokumentKravData
import java.util.UUID

class SøknadPostgresRepository(private val dataSource: HikariDataSource) :
    SøknadRepository {
    override fun hent(søknadId: UUID, ident: String): Søknad? {
        TODO("not implemented")
    }
    override fun lagre(søknad: Søknad): Boolean {
        TODO("not implemented")
    }

    override fun hentDokumentkravFor(søknadId: UUID, ident: String): Dokumentkrav {
        sjekkTilgang(ident, søknadId)
        return using(sessionOf(dataSource)) { session ->
            Dokumentkrav.rehydrer(session.hentDokumentKrav(søknadId).map { it.rehydrer() }.toSet())
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

    override fun oppdaterDokumentkrav(søknadId: UUID, ident: String, dokumentkrav: Dokumentkrav) {
        sjekkTilgang(ident, søknadId)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                dokumentkrav.toDokumentKravData().kravData.insertKravData(søknadId, transactionalSession)
            }
        }
    }

    override fun slettDokumentasjonkravFil(søknadId: UUID, ident: String, kravId: String, urn: URN) {
        sjekkTilgang(ident, søknadId)
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    statement = """
                       DELETE FROM dokumentkrav_filer_v1 
                       WHERE soknad_uuid = :soknadId
                       AND faktum_id = :kravId 
                       AND urn = :urn 
                    """.trimIndent(),
                    paramMap = mapOf(
                        "soknadId" to søknadId.toString(),
                        "kravId" to kravId,
                        "urn" to urn.toString()
                    )
                ).asUpdate
            )
        }
    }

    private fun sjekkTilgang(ident: String, søknadId: UUID) =
        if (!harTilgang(ident, søknadId)) throw IkkeTilgangExeption("Har ikke tilgang til søknadId $søknadId") else Unit
}
