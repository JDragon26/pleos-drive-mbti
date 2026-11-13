# 운전 MBTI

**차가 본 진짜 내 운전 습관 — 운전 데이터로 만드는 16가지 운전 성격 카드 + 첫 경험 배지**

브레이크·급가속, 깜빡이 습관, 크루즈 사용, 주행 시간대를 차량 데이터로 모아 4가지 축(부드러움↔다이내믹, 신중↔즉흥, 크루즈 애용↔손맛 운전, 낮↔올빼미)으로 나누고, "차분한 모범생 돌고래(SCAL)" 같은 공유용 카드로 보여 줍니다. 첫 고속도로·첫 빗길·첫 야간 같은 첫 경험 배지도 자동으로 모입니다.

## 주요 기능
| 기능 | 설명 | 차량 데이터 |
|---|---|---|
| 운전 리듬 S/D | km당 브레이크 횟수 + 급가속(3 m/s²↑) | 브레이크, 차속 |
| 깜빡이 습관 C/F | 크게 꺾기 3초 전 방향지시등 켠 비율 | 핸들 각도, 방향지시등 |
| 운전 보조 A/M | 크루즈(ADAS) 작동 중 주행 비율 | 크루즈 상태 |
| 시간대 L/N | 21~5시 주행 비율 | 시각 |
| 성격 카드 | 16유형(형용사 × 동물) + 강점·조언, 10km 이상 주행 후 공개 | — |
| 첫 경험 배지 8종 | 첫 기록·고속도로(90km/h)·빗길·야간·크루즈·동승·100km·1시간 연속 | 차속, 와이퍼, 좌석 탑승 |
| 주행/정차 화면 | 주행 중엔 "기록 중 + 배지 획득" 한 줄, P단에서 카드·축·배지 | 기어 |

## 실행 방법
1. Pleos Connect 에뮬레이터 실행 후 `adb install -r -g apk/설계3.5팀_운전MBTI_release.apk`
2. Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088`
3. 앱 "운전 MBTI" → 기어 D, 속도·핸들·깜빡이·브레이크를 바꿔 운전 → P 에서 카드 확인
4. 자동 시연: `pwsh -File tools/demo_mbti.ps1 -Style smooth -Auto` (→ SCAL), `-Style dynamic` (→ DFM?)
   - 앱에서 "기록 처음부터"를 누른 뒤 실행. 마지막 글자 L/N 은 실제 시각 기준

## 테스트 환경
- Android Studio 2026.2 / Gradle 8.11.1 / **JDK 21**
- Pleos Vehicle SDK 2.3.2, Android Automotive `android.car`(차속)
- 단위 테스트: `gradlew testDebugUnitTest` (거리 적분·4축 판정·배지·저장 6개)

## APK
`apk/설계3.5팀_운전MBTI_release.apk` (release 서명)

## 권한
| 권한 | 용도 |
|---|---|
| `pleos.car.permission.CAR_DRIVE` | 기어·브레이크·핸들·크루즈·주행거리 |
| `pleos.car.permission.CAR_LIGHTS` | 방향지시등 |
| `pleos.car.permission.CAR_SEATS` | 동승자(배지) |
| `pleos.car.permission.CAR_WINDOWS` | 와이퍼(빗길 배지) |
| `android.car.permission.CAR_SPEED` | 차속 |

## 알려진 사항
- Virtual Console 에서 크루즈는 `full_autonomous` 만 앱에 ACTIVATED 로 전달됩니다(longitudinal·lateral 은 전달 안 됨).
- 좌석 탑승 값 반전 보정(`CarHub.SEAT_SDK_INVERTED`).

## 구조
```
app/src/main/java/com/team35/drivembti/
  DriveStats.kt   운전 습관 누적·배지 (순수 로직, JSON 저장)
  Profile.kt      4축 점수 → 16유형 카드
  MainActivity.kt ViewModel + 화면
app/src/main/java/com/team35/pleoskit/   차량 신호 공통 모듈 (CarHub, UI)
```
