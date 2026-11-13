package com.team35.drivembti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {

    /** 1초 간격으로 seconds 동안 같은 속도 */
    private fun DriveStats.cruise(fromMs: Long, kph: Double, seconds: Int, hour: Int = 14, cruise: Boolean = false, rain: Boolean = false): Long {
        for (s in 0..seconds) onSample(fromMs + s * 1000L, kph, false, 0f, false, cruise, rain, 0, hour)
        return fromMs + seconds * 1000L
    }

    @Test
    fun `차속 적분으로 거리`() {
        val s = DriveStats()
        s.cruise(0, 72.0, 600) // 72km/h × 10분 = 12km
        assertEquals(12.0, s.km, 0.05)
    }

    @Test
    fun `부드럽고 신중하고 크루즈 쓰는 낮 운전자 = SCAL`() {
        val s = DriveStats()
        var t = s.cruise(0, 90.0, 600, cruise = true) // 15km 크루즈
        repeat(3) {                                     // 깜빡이 켜고 꺾기 3번
            s.onSample(t + 1000, 40.0, false, 0f, true, false, false, 0, 14)
            s.onSample(t + 2000, 40.0, false, 60f, false, false, false, 0, 14)
            s.onSample(t + 3000, 40.0, false, 0f, false, false, false, 0, 14)
            t += 3000
        }
        val p = Profile.of(s)
        assertTrue(p.ready)
        assertEquals("SCAL", p.code)
        assertEquals("차분한 모범생 돌고래", p.title)
    }

    @Test
    fun `가다 서다 + 깜빡이 없이 꺾기 + 밤 = DFMN`() {
        val s = DriveStats()
        var t = 0L
        repeat(40) {                       // 가속·급정거 반복 (밤 23시)
            t = s.cruise(t, 60.0, 30, hour = 23)
            s.onSample(t + 1000, 60.0, true, 0f, false, false, false, 0, 23)
            s.onSample(t + 2000, 0.0, false, 0f, false, false, false, 0, 23)
            s.onSample(t + 3000, 15.0, false, 0f, false, false, false, 0, 23) // 0→15km/h 1초 = 4.2m/s² 급가속
            s.onSample(t + 4000, 30.0, false, 70f, false, false, false, 0, 23) // 깜빡이 없이 꺾기
            s.onSample(t + 5000, 60.0, false, 0f, false, false, false, 0, 23)
            t += 5000
        }
        val p = Profile.of(s)
        assertEquals("DFMN", p.code)
        assertEquals("🐺", p.emoji)
    }

    @Test
    fun `10km 전에는 카드 공개 안 함`() {
        val s = DriveStats()
        s.cruise(0, 60.0, 300) // 5km
        assertFalse(Profile.of(s).ready)
    }

    @Test
    fun `첫 경험 배지는 한 번만`() {
        val s = DriveStats()
        val got = s.onSample(0, 100.0, false, 0f, false, true, true, 1, 22)
        assertTrue(Badge.HIGHWAY in got && Badge.RAIN in got && Badge.NIGHT in got && Badge.CRUISE in got && Badge.COMPANY in got)
        val again = s.onSample(1000, 100.0, false, 0f, false, true, true, 1, 22)
        assertTrue(again.isEmpty())
    }

    @Test
    fun `저장했다 불러와도 같다`() {
        val s = DriveStats()
        s.cruise(0, 100.0, 60, rain = true)
        val back = DriveStats.fromJson(s.toJson())
        assertEquals(s.km, back.km, 1e-9)
        assertEquals(s.badges, back.badges)
    }
}
