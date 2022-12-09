package no.nav.dagpenger.soknad.db

import kotliquery.Row
import java.time.ZoneId
import java.time.ZonedDateTime

object DBUtils {
    internal val tidssone = ZoneId.of("Europe/Oslo")
    internal fun Row.norskZonedDateTime(columnLabel: String): ZonedDateTime =
        this.zonedDateTime(columnLabel).withZoneSameInstant(tidssone)
}

internal class DataConstraintException(message: String) : RuntimeException(message)
