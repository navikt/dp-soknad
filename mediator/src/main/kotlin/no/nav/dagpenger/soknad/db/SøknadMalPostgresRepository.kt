package no.nav.dagpenger.soknad.db

import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.postgresql.util.PGobject
import javax.sql.DataSource

class SøknadMalPostgresRepository(private val dataSource: DataSource) : SøknadMalRepository {

    override fun lagre(søknadMal: SøknadMal) {
        using(sessionOf(dataSource)) { session: Session ->
            session.transaction { transactionalSession ->
                transactionalSession.run(
                    queryOf(
                        //language=PostgreSQL
                        "INSERT INTO soknadmal (prosessnavn, prosessversjon, mal) VALUES (:prosessnavn, :prosessversjon, :mal) ON CONFLICT DO NOTHING ",
                        mapOf(
                            "prosessnavn" to søknadMal.prosessnavn,
                            "prosessversjon" to søknadMal.prosessversjon,
                            "mal" to PGobject().apply {
                                this.type = "jsonb"
                                this.value = søknadMal.mal.toString()
                            }
                        )
                    ).asUpdate
                )
            }
        }
    }
}
