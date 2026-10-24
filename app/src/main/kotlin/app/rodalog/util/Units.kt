package app.rodalog.util

import app.rodalog.data.Settings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

const val KM_PER_MI = 1.609344
const val L_PER_GAL = 3.785411784

/** Convierte y da formato según las unidades elegidas. Internamente todo es km, L y L/100km. */
class Fmt(val s: Settings) {
    private val loc: Locale get() = Locale.getDefault()
    private val mi get() = s.distanceUnit == "MI"
    private val gal get() = s.volumeUnit == "GAL"

    val distUnit: String get() = if (mi) "mi" else "km"
    val volUnit: String get() = if (gal) "gal" else "L"
    val speedUnit: String get() = if (mi) "mph" else "km/h"
    val consUnit: String get() = when (s.consumptionUnit) {
        "KML" -> "km/L"
        "MPG" -> "mpg"
        else -> "L/100 km"
    }

    fun distVal(km: Double) = if (mi) km / KM_PER_MI else km
    fun kmFromInput(v: Double) = if (mi) v * KM_PER_MI else v
    fun volVal(l: Double) = if (gal) l / L_PER_GAL else l
    fun litersFromInput(v: Double) = if (gal) v * L_PER_GAL else v
    fun speedVal(kmh: Double) = if (mi) kmh / KM_PER_MI else kmh

    fun consVal(l100: Double): Double = when (s.consumptionUnit) {
        "KML" -> if (l100 > 0) 100.0 / l100 else 0.0
        "MPG" -> if (l100 > 0) 235.214583 / l100 else 0.0
        else -> l100
    }

    private fun n(v: Double, dec: Int) = String.format(loc, "%.${dec}f", v)

    fun dist(km: Double, dec: Int = 1) = "${n(distVal(km), dec)} $distUnit"
    fun vol(l: Double, dec: Int = 2) = "${n(volVal(l), dec)} $volUnit"
    fun speed(kmh: Double) = "${n(speedVal(kmh), 0)} $speedUnit"
    fun cons(l100: Double) = if (l100 > 0) "${n(consVal(l100), 1)} $consUnit" else "—"
    fun money(v: Double) = "${s.currency}${n(v, 2)}"
    fun num(v: Double, dec: Int = 1) = n(v, dec)

    fun date(ms: Long): String = if (ms <= 0) "—" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    fun dateTime(ms: Long): String =
        if (ms <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

    fun duration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val ss = sec % 60
        return if (h > 0) String.format(loc, "%d:%02d:%02d", h, m, ss) else String.format(loc, "%02d:%02d", m, ss)
    }
}

/** Convierte texto escrito por el usuario (acepta coma decimal) a número. */
fun String.toNum(): Double = replace(",", ".").trim().toDoubleOrNull() ?: 0.0
