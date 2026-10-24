package app.rodalog.data

/** Un intervalo entre dos cargas completas consecutivas. */
data class ConsumptionPoint(val date: Long, val l100: Double, val distanceKm: Double, val liters: Double)

object Stats {
    /** Calcula consumo por intervalo entre tanques llenos (método estándar). Lista en orden ascendente. */
    fun intervals(entriesAsc: List<FuelEntry>): List<ConsumptionPoint> {
        val out = ArrayList<ConsumptionPoint>()
        var lastFullOdo: Double? = null
        var acc = 0.0
        for (e in entriesAsc) {
            if (lastFullOdo != null) {
                acc += e.liters
                if (e.fullTank) {
                    val dist = e.odometerKm - lastFullOdo
                    if (dist > 0 && acc > 0) out.add(ConsumptionPoint(e.date, acc / dist * 100.0, dist, acc))
                    lastFullOdo = e.odometerKm
                    acc = 0.0
                }
            } else if (e.fullTank) {
                lastFullOdo = e.odometerKm
                acc = 0.0
            }
        }
        return out
    }

    /** Consumo promedio en L/100km: últimos 5 intervalos; si no hay datos usa el valor base del vehículo. */
    fun avgConsumption(entriesAsc: List<FuelEntry>, base: Double): Double {
        val last = intervals(entriesAsc).takeLast(5)
        val dist = last.sumOf { it.distanceKm }
        val liters = last.sumOf { it.liters }
        return when {
            dist > 0 && liters > 0 -> liters / dist * 100.0
            base > 0 -> base
            else -> 8.0
        }
    }
}
