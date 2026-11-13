# 운전 MBTI 시연 영상용 — 부드러운 운전(SCAL) → 기록 초기화 + 앱 재시작 → 거친 운전(DFML)
# 초기화만 하면 앱 메모리에 1부 주행 샘플이 남아 성향이 섞인다 → 재시작까지 해야 2부가 깨끗이 나온다.
# 깜빡이를 켜면 시스템 사이드뷰 카메라(ai.umos.car.evs)가 앱을 덮으므로 녹화 동안 계속 닫는다.
param([switch]$Auto)
$ErrorActionPreference = 'Stop'
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$tools = $PSScriptRoot
. "$tools\vc.ps1"

function Ui-Text {
  & $adb shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
  $enc = [Console]::OutputEncoding
  [Console]::OutputEncoding = [Text.Encoding]::UTF8
  try { (& $adb shell cat /sdcard/ui.xml) -join '' } finally { [Console]::OutputEncoding = $enc }
}

# 사이드뷰 카메라 팝업 닫기 (백그라운드)
$evs = Start-Job -ScriptBlock {
  param($adb)
  while ($true) { & $adb shell am force-stop ai.umos.car.evs 2>$null | Out-Null; Start-Sleep -Milliseconds 900 }
} -ArgumentList $adb

try {
  Start-Sleep -Seconds 3   # 맨 앞 제목 자막을 읽을 시간
  Write-Host '▶ 에뮬레이터 자동 시연 — 주행 신호를 빠르게 주입합니다'
  Start-Sleep -Seconds 4

  Write-Host '── 1부: 부드럽게 운전해 보기 ──'
  & "$tools\demo_mbti.ps1" -Style smooth -Auto:$Auto
  Start-Sleep -Seconds 1
  $card1 = [regex]::Match((Ui-Text), '>([A-Z]{4})<').Groups[1].Value
  if (-not $card1) { $card1 = [regex]::Match((Ui-Text), 'text="([A-Z]{4})"').Groups[1].Value }
  Write-Host "▶ 부드럽게 몰면 $card1 카드"
  Start-Sleep -Seconds 5

  Write-Host '▶ 거칠게 몰면? 기록을 비우고 다시 주행'
  $ok = $false
  foreach ($try in 1..5) {
    Adb-TapText '기록 처음부터'
    Start-Sleep -Seconds 1
    if ((Ui-Text) -match '누적 0\.0 km') { $ok = $true; Write-Host "  초기화 확인 (시도 $try)"; break }
  }
  if (-not $ok) { throw '기록 초기화 실패 — 녹화 중단' }
  & $adb shell am force-stop com.team35.drivembti
  & $adb shell am start -n com.team35.drivembti/.MainActivity | Out-Null
  Start-Sleep -Seconds 2

  Write-Host '── 2부: 거칠게 운전해 보기 ──'
  & "$tools\demo_mbti.ps1" -Style dynamic -Auto:$Auto
  Start-Sleep -Seconds 1
  $card2 = [regex]::Match((Ui-Text), '>([A-Z]{4})<').Groups[1].Value
  if (-not $card2) { $card2 = [regex]::Match((Ui-Text), 'text="([A-Z]{4})"').Groups[1].Value }
  # 어느 축이 바뀌었는지는 두 카드 글자를 비교해서 그대로 적는다 (시간대는 녹화 시각에 따라 달라진다)
  $axes = @('운전 리듬', '깜빡이 습관', '운전 보조', '시간대')
  $changed = @(); $same = @()
  if ($card1.Length -eq 4 -and $card2.Length -eq 4) {
    for ($i = 0; $i -lt 4; $i++) {
      if ($card1[$i] -ne $card2[$i]) { $changed += $axes[$i] } else { $same += $axes[$i] }
    }
  }
  Write-Host "▶ $card1 → $card2 — 카드가 바뀐다"
  Start-Sleep -Seconds 6
  if ($changed) {
    Write-Host ("▶ 바뀐 축: " + ($changed -join '·'))
  } else {
    Write-Host '▶ 같은 차, 같은 사람 — 운전 방식만 달랐다'
  }
  Start-Sleep -Seconds 6
} finally {
  Stop-Job $evs -ErrorAction SilentlyContinue
  Remove-Job $evs -Force -ErrorAction SilentlyContinue
}
