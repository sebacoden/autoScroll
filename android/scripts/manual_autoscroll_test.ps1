# Test manual de auto-scroll por adb (Windows / PowerShell).
#
# Automatiza lo automatizable de una sesión de auto-scroll en un emulador o device:
#   - instala el APK debug
#   - habilita el servicio de accesibilidad
#   - abre YouTube
#   - (vos navegás a Shorts a mano si hace falta)
#   - simula swipes de activación
#   - verifica si el auto-scroll arrancó (WellbeingService en foreground)
#   - simula salir de la app y verifica que la sesión termina
#   - muestra logs del servicio y las sesiones guardadas en Room
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\manual_autoscroll_test.ps1
#   (opcional) -Serial emulator-5554   para elegir device
#
# Requisitos: emulador/device con YouTube instalado. Para ver la detección de eventos,
# poné DEBUG_LOG = true en AutoScrollService y recompilá antes de correr.

param(
    [string]$Serial = "",
    [int]$Swipes = 3
)

$ErrorActionPreference = "Stop"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.freelanzer.autoscroller.debug"
$SERVICE = "$PKG/com.freelanzer.autoscroller.service.accessibility.AutoScrollService"
$APK = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"

function Adb { param([Parameter(ValueFromRemainingArguments=$true)]$args)
    if ($Serial) { & $ADB -s $Serial @args } else { & $ADB @args }
}
function WellbeingRefs {
    (Adb shell dumpsys activity services $PKG 2>&1 | Select-String "WellbeingService").Count
}

Write-Host "== 1. Instalar APK ==" -ForegroundColor Cyan
Adb install -r "$APK" | Select-Object -Last 1

Write-Host "== 2. Permiso de notificaciones + habilitar accesibilidad ==" -ForegroundColor Cyan
Adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
Adb shell settings put secure enabled_accessibility_services $SERVICE
Adb shell settings put secure accessibility_enabled 1
Start-Sleep -Seconds 2

Write-Host "== 3. Abrir YouTube ==" -ForegroundColor Cyan
Adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "   -> Navegá a la pestaña SHORTS en el emulador y presioná Enter..." -ForegroundColor Yellow
[void](Read-Host)

Write-Host "== 4. Activación: $Swipes swipes hacia arriba ==" -ForegroundColor Cyan
Adb logcat -c
$before = WellbeingRefs
for ($i = 1; $i -le $Swipes; $i++) {
    Adb shell input swipe 640 2100 640 600 200
    Start-Sleep -Milliseconds 700
}
Start-Sleep -Seconds 2
$after = WellbeingRefs
Write-Host "   WellbeingService refs: antes=$before  despues=$after  (>=1 => ACTIVADO)" -ForegroundColor Green

Write-Host "== 5. Pausa por toque (debe frenar y reanudar solo) ==" -ForegroundColor Cyan
Adb shell input tap 640 1400
Start-Sleep -Seconds 5
Write-Host "   WellbeingService refs tras pausa+resume: $(WellbeingRefs)  (>=1 => sigue activo)" -ForegroundColor Green

Write-Host "== 6. Salir de la app (Home) -> debe TERMINAR la sesion ==" -ForegroundColor Cyan
Adb shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 3
Write-Host "   WellbeingService refs tras salir: $(WellbeingRefs)  (0 => sesion terminada)" -ForegroundColor Green

Write-Host "== 7. Log del servicio ==" -ForegroundColor Cyan
Adb logcat -d -s AutoScrollSvc 2>&1 | Select-Object -Last 15

Write-Host "== 8. Sesiones guardadas en Room ==" -ForegroundColor Cyan
Adb shell run-as $PKG sqlite3 databases/autoscroller_usage.db "SELECT id, swipeCount, appPackage, (endTime-startTime)/1000 AS seg FROM scroll_sessions;" 2>&1

Write-Host "`nNota: la activacion por swipes requiere que la app emita TYPE_VIEW_SCROLLED." -ForegroundColor DarkYellow
Write-Host "Funciona en feeds RecyclerView (TikTok/IG Reels); el player fullscreen de YouTube" -ForegroundColor DarkYellow
Write-Host "Shorts NO los emite. El tap de 3 dedos es el activador universal confiable." -ForegroundColor DarkYellow
