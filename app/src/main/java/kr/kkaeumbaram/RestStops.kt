package kr.kkaeumbaram

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs

data class RestStop(val name: String, val lat: Double, val lon: Double, val road: String) {
    fun distanceKm(lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat); val dLon = Math.toRadians(lon2 - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** (lat2, lon2)에서 이 쉼터를 보는 방위각(0~360, 북=0). */
    fun bearingFrom(lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat2); val p2 = Math.toRadians(lat); val dl = Math.toRadians(lon - lon2)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
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

    /**
     * 가장 가까운 쉼터 1곳. 진행 방향(heading, 북=0)을 알면 앞쪽 ±60° 안에서 고른다.
     * 고속도로 쉼터는 반대 차로가 직선으로 더 가까운 일이 흔해서, 방향을 안 보면 못 가는 곳을 고른다.
     * ponytail: 직선거리 기준. 실제 주행거리·시간은 경유지로 넣은 뒤 내비(onWayPointInfo)가 준다.
     */
    fun pick(all: List<RestStop>, lat: Double, lon: Double, heading: Float?): RestStop? {
        val ahead = if (heading == null) all else all.filter {
            val d = abs(it.bearingFrom(lat, lon) - heading) % 360
            minOf(d, 360 - d) <= 60
        }
        return (ahead.ifEmpty { all }).minByOrNull { it.distanceKm(lat, lon) }
    }
}
