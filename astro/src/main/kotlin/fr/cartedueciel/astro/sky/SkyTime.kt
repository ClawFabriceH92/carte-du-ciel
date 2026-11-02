package fr.cartedueciel.astro.sky

import io.github.cosinekitty.astronomy.Time
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoField

/** Conversions de temps entre l'horloge système et Astronomy Engine. */
object SkyTime {
    /** Instant Astronomy Engine à partir d'une date Unix en millisecondes (UTC). */
    fun fromEpochMillis(epochMillis: Long): Time = Time.fromMillisecondsSince1970(epochMillis)

    fun toEpochMillis(time: Time): Long = time.toMillisecondsSince1970()

    /**
     * Année décimale au sens du World Magnetic Model : année + (jour de l'année − 1) / nombre de
     * jours de l'année.
     */
    fun decimalYear(epochMillis: Long): Double {
        val d = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC)
        val year = d.year
        val dayOfYear = d.get(ChronoField.DAY_OF_YEAR)
        val leap = java.time.Year.isLeap(year.toLong())
        return year + (dayOfYear - 1).toDouble() / (if (leap) 366.0 else 365.0)
    }
}
