package no.nav.dagpenger.soknad.db

import de.slub.urn.URN
import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.db.DBUtils.norskZonedDateTime
import no.nav.dagpenger.soknad.db.DBUtils.tidssone
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.serder.AktivitetsloggDTO
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.SøknadDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.KravDTO.KravTilstandDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO
import no.nav.dagpenger.soknad.serder.SøknadDTO.ProsessversjonDTO
import no.nav.dagpenger.soknad.toMap
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

@Suppress("FunctionName")
class SøknadPostgresRepository(private val dataSource: DataSource) :
    SøknadRepository {
    override fun hentEier(søknadId: UUID): String? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT person_ident
                    FROM  soknad_v1
                    WHERE uuid = :uuid
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId
                    )
                ).map { it.stringOrNull("person_ident") }.asSingle
            )
        }
    }

    override fun hent(søknadId: UUID): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                    SELECT uuid, tilstand, spraak, sist_endret_av_bruker, opprettet, person_ident
                    FROM  soknad_v1
                    WHERE uuid = :uuid AND tilstand != 'Slettet'
                    """.trimIndent(),
                    paramMap = mapOf(
                        "uuid" to søknadId
                    )
                ).map(rowToSøknadDTO(session)).asSingle
            )?.rehydrer()
        }
    }

    override fun hentSøknader(ident: String): Set<Søknad> = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf( //language=PostgreSQL
                """
                SELECT uuid, tilstand, spraak, sist_endret_av_bruker, opprettet, person_ident
                FROM  soknad_v1
                WHERE person_ident = :ident AND tilstand != 'Slettet' 
                ORDER BY sist_endret_av_bruker DESC
                """.trimIndent(),
                mapOf(
                    "ident" to ident
                )
            ).map(rowToSøknadDTO(session)).asList
        ).map { it.rehydrer() }.toSet()
    }

    private fun rowToSøknadDTO(session: Session): (Row) -> SøknadDTO {
        return { row: Row ->
            val søknadsId = UUID.fromString(row.string("uuid"))
            SøknadDTO(
                søknadsId = søknadsId,
                ident = row.string("person_ident"),
                opprettet = row.norskZonedDateTime("opprettet"),
                tilstandType = SøknadDTO.TilstandDTO.rehydrer(row.string("tilstand")),
                språkDTO = SøknadDTO.SpråkDTO(row.string("spraak")),
                dokumentkrav = SøknadDTO.DokumentkravDTO(
                    session.hentDokumentKrav(søknadsId)
                ),
                sistEndretAvBruker = row.zonedDateTime("sist_endret_av_bruker").withZoneSameInstant(tidssone),
                aktivitetslogg = session.hentAktivitetslogg(søknadsId),
                prosessversjon = session.hentProsessversjon(søknadsId),
                data = lazy {
                    SøknadDataPostgresRepository(dataSource).hentSøkerOppgave(søknadsId)
                },
            )
        }
    }

    override fun hentPåbegynteSøknader(prosessversjon: Prosessversjon): List<Søknad> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( //language=PostgreSQL
                    """
                    SELECT * 
                    FROM soknad_v1
                             LEFT JOIN soknadmal mal ON soknad_v1.soknadmal = mal.id
                    WHERE tilstand = :tilstand
                      AND ((mal.prosessnavn = :prosessnavn AND :prosessversjon > mal.prosessversjon) OR mal IS NULL) 
                    """.trimIndent(),
                    mapOf(
                        "tilstand" to Tilstand.Type.Påbegynt.toString(),
                        "prosessnavn" to prosessversjon.prosessnavn.id,
                        "prosessversjon" to prosessversjon.versjon
                    )
                ).map(rowToSøknadDTO(session)).asList
            ).map { it.rehydrer() }
        }
    }

    override fun opprett(søknadID: UUID, språk: Språk, ident: String) = Søknad(søknadID, språk, ident)

    override fun lagre(søknad: Søknad) {
        val visitor = SøknadPersistenceVisitor(søknad)
        using(sessionOf(dataSource)) { session ->
            session.transaction { transactionalSession ->
                visitor.queries().forEach {
                    transactionalSession.run(it.asUpdate)
                }
            }
        }
    }
}

internal fun Session.hentAktivitetslogg(søknadId: UUID): AktivitetsloggDTO? = run(
    queryOf(
        //language=PostgreSQL
        """
        SELECT a.data AS aktivitetslogg
        FROM aktivitetslogg_v1 AS a
        WHERE a.soknad_uuid = :soknadId
        """.trimIndent(),
        mapOf(
            "soknadId" to søknadId
        )
    ).map { row ->
        row.binaryStream("aktivitetslogg").aktivitetslogg()
    }.asSingle
)

internal fun Session.hentProsessversjon(søknadId: UUID): ProsessversjonDTO? = run(
    queryOf( //language=PostgreSQL
        """
                    SELECT prosessnavn, prosessversjon
                    FROM  soknadmal
                    JOIN soknad_v1 soknad ON soknad.soknadmal = soknadmal.id 
                    WHERE soknad.uuid = :soknadId
        """.trimIndent(),
        mapOf(
            "soknadId" to søknadId
        )
    ).map { row ->
        ProsessversjonDTO(prosessnavn = row.string("prosessnavn"), versjon = row.int("prosessversjon"))
    }.asSingle
)

private class SøknadPersistenceVisitor(søknad: Søknad) : SøknadVisitor {
    private lateinit var søknadId: UUID
    private val queries = mutableListOf<Query>()

    init {
        søknad.accept(this)
    }

    fun queries() = queries

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?
    ) {
        this.søknadId = søknadId
        queries.add(
            queryOf(
                // language=PostgreSQL
                "INSERT INTO person_v1 (ident) VALUES (:ident) ON CONFLICT DO NOTHING",
                mapOf("ident" to ident)
            )
        )
        queries.add(
            queryOf(
                // language=PostgreSQL
                """
                   INSERT INTO soknad_v1(uuid, person_ident, tilstand, spraak, opprettet, sist_endret_av_bruker, soknadmal)
                   VALUES (:uuid, :person_ident, :tilstand, :spraak, :opprettet, :sistEndretAvBruker, 
                        (SELECT id FROM soknadmal WHERE prosessnavn = :prosessnavn AND prosessversjon = :prosessversjon))
                   ON CONFLICT(uuid) DO UPDATE SET tilstand=:tilstand,
                                                sist_endret_av_bruker = :sistEndretAvBruker, 
                                                soknadmal=(SELECT id FROM soknadmal WHERE prosessnavn = :prosessnavn AND prosessversjon = :prosessversjon)
                """.trimIndent(),
                mapOf(
                    "uuid" to søknadId,
                    "person_ident" to ident,
                    "tilstand" to tilstand.tilstandType.name,
                    "spraak" to språk.verdi.toLanguageTag(),
                    "opprettet" to opprettet,
                    "sistEndretAvBruker" to sistEndretAvBruker,
                    "prosessnavn" to prosessversjon?.prosessnavn?.id,
                    "prosessversjon" to prosessversjon?.versjon
                )
            )
        )
    }

    override fun visitKrav(krav: Krav) {
        queries.add(
            queryOf(
                // language=PostgreSQL
                """
                INSERT INTO dokumentkrav_v1(faktum_id, beskrivende_id, soknad_uuid, faktum, sannsynliggjoer, tilstand, valg,
                                            begrunnelse, bundle_urn, innsendt)
                VALUES (:faktum_id, :beskrivende_id, :soknad_uuid, :faktum, :sannsynliggjoer, :tilstand, :valg, :begrunnelse, :bundle, :innsendt)
                ON CONFLICT (faktum_id, soknad_uuid) DO UPDATE SET beskrivende_id  = :beskrivende_id,
                                                                   faktum          = :faktum,
                                                                   sannsynliggjoer = :sannsynliggjoer,
                                                                   tilstand        = :tilstand,
                                                                   valg            = :valg,
                                                                   begrunnelse     = :begrunnelse,
                                                                   bundle_urn      = :bundle,
                                                                   innsendt        = :innsendt
                """.trimIndent(),
                mapOf(
                    "faktum_id" to krav.id,
                    "soknad_uuid" to søknadId,
                    "beskrivende_id" to krav.beskrivendeId,
                    "faktum" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(krav.sannsynliggjøring.faktum().originalJson())
                    },
                    "sannsynliggjoer" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(
                            krav.sannsynliggjøring.sannsynliggjør().map { it.originalJson() }
                        )
                    },
                    "tilstand" to krav.tilstand.name,
                    "valg" to krav.svar.valg.name,
                    "begrunnelse" to krav.svar.begrunnelse,
                    "bundle" to krav.svar.bundle?.toString(),
                    "innsendt" to krav.svar.innsendt
                )
            )
        )
        // fjerne alle filer for søknaden. Modellen har fasit på hvor mange som legges til i neste block
        queries.add(
            queryOf(
                // language=PostgreSQL
                "DELETE FROM dokumentkrav_filer_v1 WHERE soknad_uuid = :uuid AND faktum_id = :faktum_id",
                mapOf(
                    "uuid" to søknadId,
                    "faktum_id" to krav.id
                )
            )
        )

        queries.addAll(
            krav.svar.filer.map { fil ->
                queryOf(
                    // language=PostgreSQL
                    statement = """
                    INSERT INTO dokumentkrav_filer_v1(faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt, bundlet)
                    VALUES (:faktum_id, :soknad_uuid, :filnavn, :storrelse, :urn, :tidspunkt,:bundlet)
                    ON CONFLICT (faktum_id, soknad_uuid, urn) DO UPDATE SET filnavn = :filnavn, 
                                                                            storrelse = :storrelse, 
                                                                            tidspunkt = :tidspunkt,
                                                                            bundlet = :bundlet
                    """.trimIndent(),
                    mapOf(
                        "faktum_id" to krav.id,
                        "soknad_uuid" to søknadId,
                        "filnavn" to fil.filnavn,
                        "storrelse" to fil.storrelse,
                        "urn" to fil.urn.toString(),
                        "tidspunkt" to fil.tidspunkt,
                        "bundlet" to fil.bundlet
                    )
                )
            }
        )
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = "INSERT INTO aktivitetslogg_v1 (soknad_uuid, data) VALUES (:uuid, :data) ON CONFLICT (soknad_uuid) DO UPDATE SET data = :data",
                paramMap = mapOf(
                    "uuid" to søknadId,
                    "data" to PGobject().apply {
                        type = "jsonb"
                        value = objectMapper.writeValueAsString(aktivitetslogg.toMap())
                    }
                )
            )
        )
    }
}

private fun Session.hentDokumentKrav(søknadsId: UUID): Set<KravDTO> =
    this.run(
        queryOf(
            // language=PostgreSQL
            """
                  SELECT faktum_id, beskrivende_id, faktum, sannsynliggjoer, tilstand, valg, begrunnelse, bundle_urn, innsendt
                  FROM dokumentkrav_v1 
                  WHERE soknad_uuid = :soknad_uuid
            """.trimIndent(),
            mapOf(
                "soknad_uuid" to søknadsId
            )
        ).map { row ->
            val faktumId = row.string("faktum_id")
            KravDTO(
                id = faktumId,
                beskrivendeId = row.string("beskrivende_id"),
                sannsynliggjøring = SannsynliggjøringDTO(
                    id = faktumId,
                    faktum = objectMapper.readTree(row.binaryStream("faktum")),
                    sannsynliggjør = objectMapper.readTree(row.binaryStream("sannsynliggjoer")).toSet()
                ),
                svar = SvarDTO(
                    begrunnelse = row.stringOrNull("begrunnelse"),
                    filer = hentFiler(søknadsId, faktumId),
                    valg = SvarValgDTO.valueOf(row.string("valg")),
                    bundle = row.stringOrNull("bundle_urn")?.let { URN.rfc8141().parse(it) },
                    innsendt = row.boolean("innsendt")
                ),
                tilstand = row.string("tilstand")
                    .let { KravTilstandDTO.valueOf(it) }
            )
        }.asList
    ).toSet()

private fun Session.hentFiler(
    søknadsId: UUID,
    faktumId: String
): Set<KravDTO.FilDTO> {
    return this.run(
        queryOf(
            //language=PostgreSQL
            statement = """
                  SELECT faktum_id, soknad_uuid, filnavn, storrelse, urn, tidspunkt, bundlet
                  FROM dokumentkrav_filer_v1 
                  WHERE soknad_uuid = :soknad_uuid
                  AND faktum_id = :faktum_id 
            """.trimIndent(),
            paramMap = mapOf(
                "soknad_uuid" to søknadsId,
                "faktum_id" to faktumId
            )
        ).map { row ->
            KravDTO.FilDTO(
                filnavn = row.string("filnavn"),
                urn = URN.rfc8141().parse(row.string("urn")),
                storrelse = row.long("storrelse"),
                tidspunkt = row.norskZonedDateTime("tidspunkt"),
                bundlet = row.boolean("bundlet")
            )
        }.asList
    ).toSet()
}
