package kr.kkaeumbaram

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log

/** 차가 하는 일. 에뮬레이터/실차가 없으면 NoCar가 로그만 남긴다. */
interface Car {
    val connected: Boolean
    val log: MutableList<String>
    fun vent(strong: Boolean)
    fun ventOff()
    fun light(on: Boolean)
    fun addWaypoint(lat: Double, lon: Double, name: String)
    fun lastLocation(cb: (Double, Double) -> Unit)
    fun release()
}

class NoCar : Car {
    override val connected = false
    override val log = mutableListOf<String>()
    private fun l(s: String) { log.add(0, s); if (log.size > 30) log.removeAt(log.size - 1) }
    override fun vent(strong: Boolean) = l("vent(strong=$strong): 외기·팬${if (strong) 8 else 6}·창문${if (strong) 3 else 2}·시트통풍")
    override fun ventOff() = l("ventOff: 원복")
    override fun light(on: Boolean) = l("light($on)")
    override fun addWaypoint(lat: Double, lon: Double, name: String) = l("addWaypoint($name)")
    override fun lastLocation(cb: (Double, Double) -> Unit) = cb(37.4598, 127.0439) // 서울만남의광장 근처
    override fun release() {}
}

/**
 * Pleos Vehicle/NaviHelper/FusedLocation 어댑터.
 * 모든 호출은 onFailed로 실패를 받으므로, 실패는 로그만 남기고 흐름은 계속 간다(보수안 전제).
 */
class PleosCar(
    context: Context,
    private val onGearPark: () -> Unit,
    private val onGearDrive: () -> Unit,
    private val onChargeStart: () -> Unit,
) : Car {
    private val tag = "PleosCar"
    override val log = mutableListOf<String>()
    private fun l(s: String) { Log.i(tag, s); log.add(0, s); if (log.size > 30) log.removeAt(log.size - 1) }
    private fun fail(what: String): (Exception) -> Unit = { e -> l("$what 실패: ${e.message}") }

    private val vehicle = Vehicle(context.applicationContext)
    private val navi = NaviHelper(context.applicationContext)
    private val fused = FusedLocation(context.applicationContext)
    override var connected = false; private set

    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    // 에뮬레이터는 운전석 단독 area(0x1)를 거부한다. 11/2 실측한 area를 기본값으로 두고, capability 응답이 오면 덮어쓴다.
    // (부팅 직후엔 capability 콜백이 수십 초 안 오기도 해서 기본값이 필요)
    private val front = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
    private val cabin = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    private var fanArea: SeatArea = front
    private var recircArea: SeatArea = cabin
    private var powerArea: SeatArea = cabin
    private var vented = false
    private var ambientArea: SeatArea = cabin
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private var fanBefore = 3
    private var windowBefore = 0

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(g: VehicleGear?) {
            l("gear=$g")
            if (g == VehicleGear.GEAR_PARK) onGearPark() else if (g == VehicleGear.GEAR_DRIVE) onGearDrive()
        }
        override fun onFailed(e: Exception) = fail("gear")(e)
    }
    private val chargeListener = object : EvChargePortOpenListener {
        override fun onEvChargePortOpenUpdated(open: Boolean?) { l("chargePort=$open"); if (open == true) onChargeStart() }
        override fun onFailed(e: Exception) = fail("chargePort")(e)
    }

    init {
        runCatching { vehicle.initialize(); connected = true; l("Vehicle SDK 초기화") }
            .onFailure { l("Vehicle SDK 초기화 실패: ${it.message}") }
        if (connected) {
            runCatching { vehicle.getGear().registerGearSelection(gearListener) }.onFailure { l("기어 구독 실패: ${it.message}") }
            runCatching { vehicle.getEvBattery().registerEvChargePortOpen(chargeListener) }.onFailure { l("충전 구독 실패: ${it.message}") }
            runCatching {
                vehicle.getHVAC().checkHvacFanSpeedCapabilities({ caps: Map<SeatArea, *> ->
                    caps.keys.firstOrNull()?.let { fanArea = it; l("팬 area=${it.values.joinToString("|")}") }
                }, fail("팬 capability"))
            }.onFailure { l("팬 capability 예외: ${it.message}") }
            runCatching {
                vehicle.getHVAC().checkHvacRecirculationCapabilities({ areas: List<SeatArea> ->
                    areas.firstOrNull()?.let { recircArea = it; l("외기 area=${it.values.joinToString("|")}") }
                }, fail("외기 capability"))
                vehicle.getHVAC().checkHvacPowerCapabilities({ areas: List<SeatArea> ->
                    areas.firstOrNull()?.let { powerArea = it; l("공조 전원 area=${it.values.joinToString("|")}") }
                }, fail("공조 전원 capability"))
                vehicle.getLight().checkAmbientLightBrightnessCapabilities({ _, caps: Map<SeatArea, *> ->
                    caps.keys.firstOrNull()?.let { ambientArea = it; l("앰비언트 area=${it.values.joinToString("|")}") }
                }, fail("앰비언트 capability"))
            }.onFailure { l("capability 예외: ${it.message}") }
        }
        runCatching { navi.initialize({ l("NaviHelper 연결") }, { l("NaviHelper 실패: ${it.message}") }) }
            .onFailure { l("NaviHelper 초기화 실패: ${it.message}") }
        runCatching { fused.initialize(); l("FusedLocation 연결") }
            .onFailure { l("FusedLocation 초기화 실패: ${it.message}") }
    }

    override fun vent(strong: Boolean) {
        if (!connected) return l("vent: 차량 미연결")
        vented = true
        val hvac = vehicle.getHVAC()
        // 팬은 공조 전원이 켜진 뒤에만 받는다(error code 2) → 전원 콜백 뒤에 순서대로
        val fan = { hvac.setHvacFanSpeed(fanArea, if (strong) 8 else 6, { _, v -> l("팬 $v") }, fail("팬")) }
        hvac.setHvacPower(powerArea, true, { _, _ -> l("공조 ON"); fan() }, { e -> fail("공조 ON")(e); fan() })
        hvac.setHvacRecirculation(recircArea, false, { _, _ -> l("외기 전환") }, fail("외기"))
        vehicle.getSeat().setHvacSeatVentilation(driver, 3, { _, v -> l("시트 통풍 $v") }, fail("시트 통풍"))
        // 와이퍼 작동 중(비)이면 창문은 생략. 에뮬레이터 창문 0~10단계, 15% ≈ 2.
        vehicle.getWiper().getCurrentWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), { w ->
            if (w == WiperState.OFF || w == null) {
                vehicle.getWindow().getCurrentWindowPosition(driverWindow, { p -> windowBefore = p ?: 0 }, fail("창문 읽기"))
                vehicle.getWindow().setWindowPosition(driverWindow, if (strong) 3 else 2, { _, v -> l("창문 $v") }, fail("창문"))
            } else l("와이퍼 $w → 창문 생략")
        }, { vehicle.getWindow().setWindowPosition(driverWindow, 2, { _, v -> l("창문 $v") }, fail("창문")) })
    }

    override fun ventOff() {
        if (!connected || !vented) return
        vented = false
        vehicle.getHVAC().setHvacFanSpeed(fanArea, fanBefore, { _, v -> l("팬 원복 $v") }, fail("팬 원복"))
        vehicle.getSeat().setHvacSeatVentilation(driver, 0, { _, _ -> l("시트 통풍 끔") }, fail("시트 통풍"))
        vehicle.getWindow().setWindowPosition(driverWindow, windowBefore, { _, v -> l("창문 원복 $v") }, fail("창문 원복"))
    }

    override fun light(on: Boolean) {
        if (!connected) return
        vehicle.getLight().setAmbientLightBrightness(ambientArea, if (on) 100 else 10, { _, v -> l("앰비언트 $v") }, fail("앰비언트"))
    }

    override fun addWaypoint(lat: Double, lon: Double, name: String) {
        runCatching {
            navi.addWaypoint(RequestWaypointInfo(lat, lon, name, WaypointIndex.FIRST, "", "", "", ""))
            l("경유지 추가: $name")
        }.onFailure { l("경유지 실패: ${it.message}") }
    }

    override fun lastLocation(cb: (Double, Double) -> Unit) {
        runCatching {
            fused.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(d: LocationData?) { if (d != null) cb(d.latitude, d.longitude) else cb(37.4598, 127.0439) }
                override fun onError(e: Exception) { l("위치 실패: ${e.message}"); cb(37.4598, 127.0439) }
            })
        }.onFailure { l("위치 호출 실패: ${it.message}"); cb(37.4598, 127.0439) }
    }

    override fun release() {
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getEvBattery().unregisterEvChargePortOpen(chargeListener)
            vehicle.release(); navi.release(); fused.release()
        }
    }
}
