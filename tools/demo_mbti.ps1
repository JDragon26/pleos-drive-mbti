# 운전 MBTI 시연 — 운전 스타일 두 가지를 Virtual Console 로 재현한다
#   pwsh -File tools\demo_mbti.ps1 -Style smooth [-Auto]   부드럽게·깜빡이 정석·크루즈 (→ S C A ?)
#   pwsh -File tools\demo_mbti.ps1 -Style dynamic [-Auto]  급가속·급제동·깜빡이 없이 꺾기 (→ D F M ?)
# 앱에서 "기록 처음부터"를 누른 뒤 실행. 마지막 글자(L/N)는 실제 시각(21~5시면 N)
param([ValidateSet('smooth', 'dynamic')][string]$Style = 'smooth', [switch]$Auto)
. "$PSScriptRoot\vc.ps1"

Vc-Step '준비 — 주차(P), 운전자·조수석 탑승' -Auto:$Auto
Vc-Gear 'Park'; Vc-Speed 0; Vc-Cruise 'ready'; Vc-Wiper 'Off'; Vc-Turn 'Off'; Vc-Steer 0
Vc-Seat 'driver' $true; Vc-Seat 'passenger' $true

Write-Host '▶ 이제 출발(D)' -ForegroundColor Yellow
Vc-Gear 'Drive'
Say-WhenUi '운전 습관 기록 중' '운전 습관을 기록하기 시작'
Start-Sleep -Seconds 3

if ($Style -eq 'smooth') {
    Vc-Speed 60; Start-Sleep 1; Vc-Speed 90
    Vc-Step '고속도로 크루즈 켬 → 운전 보조 축' -Auto:$Auto
    Vc-Cruise 'full_autonomous'
    foreach ($i in 1..3) {
        Vc-Step "깜빡이 켜고 차선 변경 $i → 깜빡이 축" -Auto:$Auto
        Vc-Turn 'BlinkLeft'; Start-Sleep -Milliseconds 800; Vc-Steer 60; Start-Sleep -Milliseconds 600; Vc-Steer 0; Vc-Turn 'Off'
        Vc-OdoAdd 4
    }
    Vc-Cruise 'ready'
} else {
    # 영상 자막용으로 구간마다 무엇을 하는지 알린다 (▶ 로 시작하는 줄이 자막이 된다)
    $say = @(
        '급가속 반복 → 운전 리듬 축',
        '깜빡이 없이 핸들 꺾기 → 깜빡이 축',
        '급제동 · 크루즈 안 씀 → 운전 보조 축'
    )
    # 카드는 누적 10km 부터, 성향은 km 당 사건 수로 판단한다 → 1km 마다 한 번씩 12번
    # (구간을 2km 로 늘려 6번만 하면 사건 밀도가 절반이 되어 '부드러움'으로 판정된다)
    foreach ($i in 1..12) {
        if ((($i - 1) % 4) -eq 0) { Write-Host ("▶ " + $say[([math]::Floor(($i - 1) / 4))]) -ForegroundColor Yellow }
        Write-Host "  가다 서다 $i" -ForegroundColor DarkGray
        Vc-Speed 0; Start-Sleep 1.2; Vc-Speed 55; Start-Sleep 1.2   # 급가속
        Vc-Steer 70; Start-Sleep -Milliseconds 500; Vc-Steer 0      # 깜빡이 없이 꺾기
        Vc-Brake 70; Start-Sleep -Milliseconds 300; Vc-Brake 0       # 급제동
        Vc-OdoAdd 1
    }
}

Write-Host '▶ 이제 정차(P)' -ForegroundColor Yellow
Vc-Speed 0; Vc-Gear 'Park'
Say-WhenUi -Gone '카드 준비 중' '운전 데이터로 만든 카드가 나왔다'
Write-Host '끝' -ForegroundColor Green
