package com.team35.drivembti

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team35.pleoskit.AppTheme
import com.team35.pleoskit.CarHub
import com.team35.pleoskit.CarState
import com.team35.pleoskit.DrivingView
import com.team35.pleoskit.Palette
import com.team35.pleoskit.Panel
import com.team35.pleoskit.Seat
import com.team35.pleoskit.Signal
import com.team35.pleoskit.Title
import org.json.JSONObject
import java.util.Calendar

private val Accent = Color(0xFFB388FF)

class MbtiViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("mbti", Context.MODE_PRIVATE)
    private val stats = prefs.getString("stats", null)?.let { runCatching { DriveStats.fromJson(JSONObject(it)) }.getOrNull() } ?: DriveStats()
    private val main = Handler(Looper.getMainLooper())

    var car by mutableStateOf(CarState())
        private set
    var profile by mutableStateOf(Profile.of(stats))
        private set
    var badges by mutableStateOf(stats.badges.toList())
        private set
    var newBadge by mutableStateOf<Badge?>(null)
        private set
    var maxKph by mutableStateOf(stats.maxKph)
        private set

    private val hub = CarHub(app, setOf(Signal.DRIVE, Signal.SPEED, Signal.SEATS, Signal.WINDOWS)) { old, new ->
        car = new
        sample()
        odometer(old.odometerKm, new.odometerKm)
    }

    /** 계기 주행거리가 차속 적분보다 많이 늘었으면 차이만큼 보탠다 (실차는 1km 단위, 시연은 거리 점프) */
    private var kmAtLastOdo = stats.km
    private fun odometer(old: Double?, new: Double?) {
        if (old == null || new == null || new <= old) return
        val delta = new - old
        if (delta > 50) { kmAtLastOdo = stats.km; return } // 시뮬레이터 큰 점프는 무시
        val extra = delta - (stats.km - kmAtLastOdo)
        val s = car
        stats.addKm(extra, s.cruise, s.wiper, s.occupied.count { it.value && it.key != Seat.DRIVER }, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        kmAtLastOdo = stats.km
        sample()
    }

    /** 속도가 일정하면 변화 이벤트가 안 오므로 1초마다 한 번씩도 반영 (거리 적분) */
    private val ticker = object : Runnable {
        override fun run() {
            sample()
            main.postDelayed(this, 1000)
        }
    }
    private var lastSave = 0L

    init {
        hub.start()
        main.post(ticker)
    }

    private fun sample() {
        val s = car
        val now = SystemClock.elapsedRealtime()
        val got = stats.onSample(
            atMs = now,
            kph = s.speedKph ?: 0.0,
            brake = s.brake,
            steering = s.steering,
            signalOn = s.turn == TurnSignalValue.LEFT || s.turn == TurnSignalValue.RIGHT,
            cruise = s.cruise,
            rain = s.wiper,
            passengers = s.occupied.count { it.value && it.key != Seat.DRIVER },
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        )
        if (got.isNotEmpty()) newBadge = got.last()
        profile = Profile.of(stats)
        badges = stats.badges.toList()
        maxKph = stats.maxKph
        if (now - lastSave > 5000) save()
    }

    private fun save() {
        lastSave = SystemClock.elapsedRealtime()
        prefs.edit().putString("stats", stats.toJson().toString()).apply()
    }

    fun dismissBadge() { newBadge = null }

    fun reset() {
        prefs.edit().remove("stats").apply()
        val fresh = DriveStats()
        stats.apply {
            km = fresh.km; brakes = 0; hardAccels = 0; turns = 0; signaledTurns = 0; cruiseKm = 0.0
            nightKm = 0.0; rainKm = 0.0; withPassengerKm = 0.0; maxKph = 0.0; longestStintMin = 0.0; badges.clear()
        }
        sample()
    }

    override fun onCleared() {
        main.removeCallbacks(ticker)
        save()
        hub.stop()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(Accent) {
                val vm: MbtiViewModel = viewModel()
                if (!vm.car.parked) {
                    val b = vm.newBadge
                    DrivingView(
                        "운전 습관 기록 중", "%.1f km".format(vm.profile.km), Accent,
                        if (b != null) "${b.emoji} ${b.label} 배지 획득!" else "정차하면 내 운전 카드를 볼 수 있어요",
                    )
                } else {
                    ParkedScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun ParkedScreen(vm: MbtiViewModel) {
    val p = vm.profile
    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Title("운전 MBTI", "차가 본 진짜 내 운전 습관")
            if (p.ready) Card(p) else Preparing(p)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel {
                Text("성향 4가지", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                p.axes.forEach { AxisBar(it) }
            }
            Badges(vm.badges)
            Panel {
                Text("기록", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("누적 %.1f km · 최고 %.0f km/h".format(p.km, vm.maxKph), fontSize = 18.sp, color = Palette.Dim)
            }
            OutlinedButton(onClick = { vm.reset() }) { Text("기록 처음부터") }
        }
    }
}

/** 공유용 카드 (숏폼·SNS 캡처 크기) */
@Composable
private fun Card(p: Profile) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF4527A0), Color(0xFF7C4DFF), Color(0xFFE040FB))), RoundedCornerShape(24.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(p.emoji, fontSize = 88.sp)
        Text(p.code, fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(p.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(p.summary, fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Text("💪 ${p.strength}", fontSize = 18.sp, color = Color.White)
        Text("💡 ${p.tip}", fontSize = 18.sp, color = Color.White)
        Text("%.0f km 운전 데이터 기반 · Pleos".format(p.km), fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Preparing(p: Profile) {
    Panel {
        Text("🔒 카드 준비 중", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("%.1f / %.0f km — 조금만 더 달리면 내 운전 성격이 나와요".format(p.km, Profile.MIN_KM), fontSize = 18.sp, color = Palette.Dim)
        Box(Modifier.fillMaxWidth().height(14.dp).background(Color(0xFF33363D), RoundedCornerShape(7.dp))) {
            Box(Modifier.fillMaxWidth((p.km / Profile.MIN_KM).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Accent, RoundedCornerShape(7.dp)))
        }
    }
}

@Composable
private fun AxisBar(a: Axis) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(a.left, Modifier.weight(1f), fontSize = 16.sp, color = if (a.letter == a.leftLetter) Accent else Palette.Dim)
            Text(a.title, fontSize = 14.sp, color = Palette.Dim)
            Text(a.right, Modifier.weight(1f), fontSize = 16.sp, color = if (a.letter == a.rightLetter) Accent else Palette.Dim,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF33363D), RoundedCornerShape(5.dp))) {
            val pos = a.score.toFloat().coerceIn(0.03f, 0.97f)
            Box(Modifier.fillMaxWidth(pos).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(18.dp).fillMaxHeight().background(Accent, RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun Badges(got: List<Badge>) {
    Panel {
        Text("첫 경험 배지 ${got.size}/${Badge.entries.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Badge.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    val has = b in got
                    Column(
                        Modifier.weight(1f).background(if (has) Color(0xFF3A2E5C) else Color(0xFF1B1D22), RoundedCornerShape(12.dp)).padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (has) b.emoji else "❔", fontSize = 30.sp)
                        Text(b.label, fontSize = 13.sp, color = if (has) Color.White else Palette.Dim)
                    }
                }
            }
        }
    }
}
