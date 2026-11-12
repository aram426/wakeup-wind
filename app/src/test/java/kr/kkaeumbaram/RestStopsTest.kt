package kr.kkaeumbaram

import org.junit.Assert.assertEquals
import org.junit.Test

class RestStopsTest {
    // 서울만남의광장(37.4598, 127.0439) 기준. 북쪽 10km, 남쪽 3km에 쉼터 하나씩.
    private val north = RestStop("북쪽", 37.5498, 127.0439, "A")
    private val south = RestStop("남쪽", 37.4328, 127.0439, "B")

    @Test fun `방향을 모르면 직선 최단`() = assertEquals(south, RestStops.pick(listOf(north, south), 37.4598, 127.0439, null))

    @Test fun `북쪽으로 달리면 가까운 남쪽 대신 앞쪽 북쪽`() = assertEquals(north, RestStops.pick(listOf(north, south), 37.4598, 127.0439, 10f))

    @Test fun `앞쪽에 하나도 없으면 전체에서 최단`() = assertEquals(south, RestStops.pick(listOf(south), 37.4598, 127.0439, 0f))
}
