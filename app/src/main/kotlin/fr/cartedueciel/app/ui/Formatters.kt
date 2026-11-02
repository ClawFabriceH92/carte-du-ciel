package fr.cartedueciel.app.ui

import android.content.Context
import android.text.format.DateUtils
import fr.cartedueciel.app.R
import fr.cartedueciel.app.data.DistanceUnit
import io.github.cosinekitty.astronomy.Time
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {
    fun degrees(value: Double, decimals: Int = 1): String =
        String.format(Locale.getDefault(), "%.${decimals}f°", value)

    fun signedDegrees(value: Double, decimals: Int = 1): String =
        String.format(Locale.getDefault(), "%+.${decimals}f", value)

    fun number(value: Double, decimals: Int = 1): String =
        String.format(Locale.getDefault(), "%.${decimals}f", value)

    fun magnitude(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    fun coordinate(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "O"
        return String.format(Locale.getDefault(), "%.2f° %s, %.2f° %s", kotlin.math.abs(lat), ns, kotlin.math.abs(lon), ew)
    }

    fun starDistance(context: Context, parsecs: Double?, unit: DistanceUnit): String? {
        parsecs ?: return null
        return when (unit) {
            DistanceUnit.LIGHT_YEARS -> context.getString(R.string.object_distance_ly, bigNumber(parsecs * 3.26156))
            DistanceUnit.PARSECS -> context.getString(R.string.object_distance_pc, bigNumber(parsecs))
        }
    }

    fun bodyDistance(context: Context, au: Double, isMoon: Boolean): String =
        if (isMoon) context.getString(R.string.object_distance_km, bigNumber(au * 149_597_870.7, 0))
        else context.getString(R.string.object_distance_au, number(au, 3))

    private fun bigNumber(v: Double, decimals: Int = 1): String =
        if (v >= 1000) String.format(Locale.getDefault(), "%,.0f", v) else String.format(Locale.getDefault(), "%.${decimals}f", v)

    /** Heure locale ; précédée du jour abrégé si ce n'est pas aujourd'hui. */
    fun time(context: Context, time: Time?): String? {
        time ?: return null
        val ms = time.toMillisecondsSince1970()
        val timeText = android.text.format.DateFormat.getTimeFormat(context).format(Date(ms))
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) timeText else DateUtils.formatDateTime(context, ms, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY) + " " + timeText
    }
}
