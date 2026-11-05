package kr.kkaeumbaram

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RestStop(val name: String, val lat: Double, val lon: Double, val road: String) {
    fun distanceKm(lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat); val dLon = Math.toRadians(lon2 - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

object RestStops {
    fun load(context: Context): List<RestStop> =
        context.assets.open("rest_stops.csv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val c = line.split(",")
                if (c.size < 4) null else runCatching { RestStop(c[0], c[1].toDouble(), c[2].toDouble(), c[3]) }.getOrNull()
            }

    fun nearest(all: List<RestStop>, lat: Double, lon: Double, n: Int = 3) =
        all.sortedBy { it.distanceKm(lat, lon) }.take(n)
}
