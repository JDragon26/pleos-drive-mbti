package com.team35.drivembti

import org.json.JSONObject
import kotlin.math.abs

/**
 * 운전 습관 누적 통계. 차량 신호를 받을 때마다 갱신하고 JSON 으로 저장한다.
 * 순수 로직(시각·속도·상태만 받음)이라 JVM 테스트로 검증한다.
 */
class DriveStats {
    var km = 0.0
    var brakes = 0            // 브레이크 밟은 횟수
    var hardAccels = 0        // 급가속 (3 m/s² 이상)
    var turns = 0             // 주행 중 핸들 크게 꺾은 횟수 (|각도| > 45°)
    var signaledTurns = 0     // 그중 3초 안에 깜빡이를 켰던 것
    var cruiseKm = 0.0
    var nightKm = 0.0         // 21시 ~ 5시
    var rainKm = 0.0
    var withPassengerKm = 0.0
    var maxKph = 0.0
    var longestStintMin = 0.0 // 쉬지 않고 이어서 운전한 최장 시간
    val badges = linkedSetOf<Badge>()

    // 진행 중 상태 (저장 안 함)
    private var lastMs: Long? = null
    private var lastKph = 0.0
    private var brakeDown = false
    private var turning = false
    private var lastSignalMs = Long.MIN_VALUE / 2
    private var stintMs = 0L

    /**
     * 신호 한 번 반영. hour = 현재 시각(0~23).
     * 거리는 차속 적분 (Virtual Console 직접 운전은 주행거리를 안 늘려서).
     */
    fun onSample(
        atMs: Long, kph: Double, brake: Boolean, steering: Float, signalOn: Boolean,
        cruise: Boolean, rain: Boolean, passengers: Int, hour: Int,
    ): List<Badge> {
        val prev = lastMs
        lastMs = atMs
        if (signalOn) lastSignalMs = atMs
        val moving = kph > 3
        if (prev != null) {
            val dtMs = (atMs - prev).coerceIn(0, 10_000)
            addKm((lastKph + kph) / 2 * dtMs / 3_600_000.0, cruise, rain, passengers, hour)
            val accel = (kph - lastKph) / 3.6 / (dtMs / 1000.0).coerceAtLeast(0.05)
            if (accel > HARD_ACCEL && dtMs in 1..3000) hardAccels++
            stintMs = if (moving || kph > 0) stintMs + dtMs else 0
            longestStintMin = maxOf(longestStintMin, stintMs / 60_000.0)
        }
        lastKph = kph
        maxKph = maxOf(maxKph, kph)
        if (brake && !brakeDown && moving) brakes++
        brakeDown = brake
        val bigTurn = abs(steering) > 45 && moving
        if (bigTurn && !turning) {
            turns++
            if (atMs - lastSignalMs <= 3_000) signaledTurns++
        }
        turning = bigTurn
        return newBadges(cruise, rain, hour, passengers, moving)
    }

    /** 거리 추가 (차속 적분 또는 계기 주행거리 보정) */
    fun addKm(dKm: Double, cruise: Boolean, rain: Boolean, passengers: Int, hour: Int) {
        if (dKm <= 0) return
        km += dKm
        if (cruise) cruiseKm += dKm
        if (hour >= 21 || hour < 5) nightKm += dKm
        if (rain) rainKm += dKm
        if (passengers > 0) withPassengerKm += dKm
    }

    private fun newBadges(cruise: Boolean, rain: Boolean, hour: Int, passengers: Int, moving: Boolean): List<Badge> {
        val got = mutableListOf<Badge>()
        fun give(b: Badge, cond: Boolean) { if (cond && badges.add(b)) got += b }
        give(Badge.FIRST_DRIVE, km >= 1)
        give(Badge.HIGHWAY, maxKph >= 90)
        give(Badge.RAIN, rain && moving)
        give(Badge.NIGHT, moving && (hour >= 21 || hour < 5))
        give(Badge.CRUISE, cruise && moving)
        give(Badge.COMPANY, passengers > 0 && moving)
        give(Badge.KM100, km >= 100)
        give(Badge.HOUR, longestStintMin >= 60)
        return got
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("km", km); put("brakes", brakes); put("hardAccels", hardAccels)
        put("turns", turns); put("signaledTurns", signaledTurns); put("cruiseKm", cruiseKm)
        put("nightKm", nightKm); put("rainKm", rainKm); put("withPassengerKm", withPassengerKm)
        put("maxKph", maxKph); put("longestStintMin", longestStintMin)
        put("badges", badges.joinToString(",") { it.name })
    }

    companion object {
        const val HARD_ACCEL = 3.0 // m/s²

        fun fromJson(o: JSONObject) = DriveStats().apply {
            km = o.optDouble("km", 0.0); brakes = o.optInt("brakes"); hardAccels = o.optInt("hardAccels")
            turns = o.optInt("turns"); signaledTurns = o.optInt("signaledTurns"); cruiseKm = o.optDouble("cruiseKm", 0.0)
            nightKm = o.optDouble("nightKm", 0.0); rainKm = o.optDouble("rainKm", 0.0)
            withPassengerKm = o.optDouble("withPassengerKm", 0.0)
            maxKph = o.optDouble("maxKph", 0.0); longestStintMin = o.optDouble("longestStintMin", 0.0)
            o.optString("badges").split(',').filter { it.isNotBlank() }
                .mapNotNull { n -> Badge.entries.firstOrNull { it.name == n } }.forEach { badges += it }
        }
    }
}

enum class Badge(val emoji: String, val label: String) {
    FIRST_DRIVE("🚗", "첫 기록"),
    HIGHWAY("🛣", "첫 고속도로"),
    RAIN("🌧", "첫 빗길"),
    NIGHT("🌙", "첫 야간 운전"),
    CRUISE("🤖", "첫 크루즈"),
    COMPANY("👫", "첫 동승"),
    KM100("💯", "100km 돌파"),
    HOUR("⏱", "1시간 연속 운전"),
}
