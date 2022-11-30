package no.nav.dagpenger.soknad.innsending

import kotliquery.Query
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.InnsendingVisitor
import no.nav.dagpenger.soknad.db.DBUtils.norskZonedDateTime
import no.nav.dagpenger.soknad.db.DataConstraintException
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

internal class InnsendingPostgresRepository(private val ds: DataSource) : InnsendingRepository {

    override fun hent(innsendingId: UUID): Innsending? {
        return using(sessionOf(ds)) { session ->
            // todo fix me
            val hovedDokument = null
            val dokumenter = emptyList<Innsending.Dokument>()
            val metadata = null
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT *
                    FROM innsending_v1
                    WHERE innsending_uuid = :innsending_uuid 
                    """.trimIndent(),
                    paramMap = mapOf(
                        "innsending_uuid" to innsendingId
                    )
                ).map { row ->
                    val dialogId = row.uuid("soknad_uuid")
                    Innsending.rehydrer(
                        innsendingId = row.uuid("innsending_uuid"),
                        type = Innsending.InnsendingType.valueOf(row.string("innsendingtype")),
                        ident = session.hentIdent(dialogId),
                        søknadId = dialogId,
                        innsendt = row.norskZonedDateTime("innsendt"),
                        journalpostId = row.stringOrNull("journalpost_id"),
                        tilstandsType = Innsending.TilstandType.valueOf(row.string("tilstand")),
                        hovedDokument = hovedDokument,
                        dokumenter = dokumenter,
                        metadata = metadata
                    )
                }.asSingle
            )
        }
    }

    override fun lagre(innsending: Innsending) {
        using(sessionOf(ds)) { session ->
            session.transaction { tx ->
                InnsendingPersistenceVisitor(innsending = innsending).queries().forEach {
                    tx.run(it.asUpdate)
                }
            }
        }
    }

    override fun finnFor(søknadsId: UUID): List<Innsending> {
        TODO("Not yet implemented")
    }

    private fun Session.hentIdent(dialogId: UUID): String {
        return this.run(
            queryOf(
                statement = """SELECT person_ident FROM soknad_v1 WHERE uuid = :uuid""",
                paramMap = mapOf(
                    "uuid" to dialogId
                )
            ).map { row -> row.string("person_ident") }.asSingle
        ) ?: throw DataConstraintException("Fant ikke ident for dialogId: $dialogId")
    }
}

private class InnsendingPersistenceVisitor(innsending: Innsending) : InnsendingVisitor {
    private val queries: MutableList<Query> = mutableListOf()

    init {
        innsending.accept(this)
    }

    fun queries(): List<Query> = queries

    override fun visit(
        innsendingId: UUID,
        søknadId: UUID,
        ident: String,
        innsendingType: Innsending.InnsendingType,
        tilstand: Innsending.TilstandType,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        dokumenter: List<Innsending.Dokument>,
        metadata: Innsending.Metadata?,
    ) {
        val element = queryOf(
            //language=PostgreSQL
            statement = """INSERT INTO innsending_v1(innsending_uuid, soknad_uuid, innsendt, journalpost_id, innsendingtype, tilstand) VALUES(:innsending_uuid, :soknad_uuid, :innsendt, :journalpost_id, :innsendingtype, :tilstand)""",
            paramMap = mutableMapOf(
                "innsending_uuid" to innsendingId,
                "soknad_uuid" to søknadId,
                "innsendt" to innsendt,
                "journalpost_id" to journalpost,
                "tilstand" to tilstand.name,
                "innsendingtype" to innsendingType.name
            )
        )
        queries.add(element)
    }
}
