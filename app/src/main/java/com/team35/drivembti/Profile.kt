package com.team35.drivembti

import kotlin.math.min

/** 축 하나: 왼쪽(0) ↔ 오른쪽(1) 점수와 글자 */
data class Axis(val title: String, val left: String, val right: String, val leftLetter: Char, val rightLetter: Char, val score: Double, val threshold: Double) {
    val letter get() = if (score >= threshold) rightLetter else leftLetter
    val word get() = if (score >= threshold) right else left
}

/** 운전 성향 카드 */
data class Profile(val axes: List<Axis>, val km: Double) {
    val code get() = axes.joinToString("") { it.letter.toString() }
    val ready get() = km >= MIN_KM

    private val styleKey get() = "${axes[0].letter}${axes[1].letter}"
    private val habitKey get() = "${axes[2].letter}${axes[3].letter}"

    val title get() = "${ADJ.getValue(styleKey)} ${ANIMAL.getValue(habitKey).second}"
    val emoji get() = ANIMAL.getValue(habitKey).first
    val summary get() = axes.joinToString(" · ") { it.word }

    /** 강점 한 줄 + 조언 한 줄 */
    val strength get() = when (styleKey) {
        "SC" -> "동승자가 가장 편안해하는 운전. 깜빡이까지 정석이에요."
        "SF" -> "부드럽게 흐르듯 달려요. 승차감은 최고!"
        "DC" -> "판단이 빠르고 신호도 확실히 주는 승부사."
        else -> "막힘없이 시원하게 달리는 모험가."
    }
    val tip get() = when {
        axes[1].letter == 'F' -> "차선 바꾸기 3초 전 깜빡이 한 번이면 완벽해요."
        axes[0].letter == 'D' -> "브레이크를 한 박자 일찍, 살살 밟으면 전비가 좋아져요."
        axes[2].letter == 'M' -> "고속도로에선 크루즈를 켜 보세요. 피로가 확 줄어요."
        else -> "지금 습관 그대로! 장거리에선 2시간마다 쉬어 가요."
    }

    companion object {
        const val MIN_KM = 10.0

        private val ADJ = mapOf("SC" to "차분한 모범생", "SF" to "느긋한 낭만파", "DC" to "칼같은 승부사", "DF" to "거침없는 모험가")
        private val ANIMAL = mapOf(
            "AL" to ("🐬" to "돌고래"), "AN" to ("🦉" to "부엉이"),
            "ML" to ("🐎" to "야생마"), "MN" to ("🐺" to "늑대"),
        )

        fun of(s: DriveStats): Profile {
            val km = s.km.coerceAtLeast(0.001)
            val dynamic = 0.5 * min(1.0, s.brakes / km / 4.0) + 0.5 * min(1.0, s.hardAccels / km / 1.0)
            val careless = if (s.turns >= 3) 1 - s.signaledTurns.toDouble() / s.turns else 0.3
            val assist = s.cruiseKm / km
            val night = s.nightKm / km
            return Profile(
                listOf(
                    Axis("운전 리듬", "부드러움", "다이내믹", 'S', 'D', dynamic, 0.4),
                    Axis("깜빡이 습관", "신중", "즉흥", 'C', 'F', careless, 0.4),
                    Axis("운전 보조", "크루즈 애용", "손맛 운전", 'A', 'M', 1 - assist, 0.7),
                    Axis("시간대", "낮 운전", "올빼미", 'L', 'N', night, 0.3),
                ),
                s.km,
            )
        }
    }
}
