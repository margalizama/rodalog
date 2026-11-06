package app.rodalog.data

import android.location.Location

data class RoutePoint(val lat: Double, val lon: Double, val kmh: Int)

/** Lee la ruta guardada ("lat,lon,kmh;lat,lon,kmh"). Acepta también el formato viejo "lat,lon". */
fun parseRoute(route: String): List<RoutePoint> =
    route.split(";").mapNotNull { s ->
        val p = s.split(",")
        if (p.size < 2) {
            null
        } else {
            val la = p[0].toDoubleOrNull()
            val lo = p[1].toDoubleOrNull()
            val sp = if (p.size >= 3) (p[2].toIntOrNull() ?: 0) else 0
            if (la != null && lo != null) RoutePoint(la, lo, sp) else null
        }
    }

/** Distancia en metros entre dos coordenadas. */
fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, r)
    return r[0].toDouble()
}

/**
 * Modelo sencillo de consumo según la velocidad: la gasolina rinde menos en tráfico lento
 * y a velocidades muy altas. El factor se normaliza con el perfil de velocidades de tus
 * viajes anteriores, para que el promedio siga coincidiendo con tu consumo real por tanque.
 */
object ConsumptionModel {
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)

    fun factor(kmh: Double): Double = when {
        kmh <= 20.0 -> 1.5
        kmh <= 40.0 -> lerp(1.5, 1.2, (kmh - 20.0) / 20.0)
        kmh <= 60.0 -> lerp(1.2, 1.0, (kmh - 40.0) / 20.0)
        kmh <= 80.0 -> 1.0
        kmh <= 110.0 -> lerp(1.0, 1.25, (kmh - 80.0) / 30.0)
        else -> lerp(1.25, 1.6, (kmh - 110.0) / 30.0)
    }

    /** Factor promedio de los viajes recientes (con velocidad registrada); 1.1 si aún hay pocos datos. */
    fun normFrom(trips: List<Trip>): Double {
        var sum = 0.0
        var n = 0
        for (t in trips) {
            for (p in parseRoute(t.route)) {
                if (p.kmh > 0) {
                    sum += factor(p.kmh.toDouble())
                    n++
                }
            }
        }
        return if (n >= 50) sum / n else 1.1
    }
}
