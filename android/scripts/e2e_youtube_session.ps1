# E2E manual de una sesion de auto-scroll en YouTube Shorts (Windows / PowerShell).
# (Texto ASCII a proposito: powershell.exe 5.1 lee .ps1 en ANSI y rompe acentos.)
#
# Flujo (simple y generico), partiendo de la pantalla de inicio:
#   1. Abre YouTube.
#   2. Toca la pestania Shorts.
#   3. Hace 3 swipes hacia arriba para activar el auto-scroller.
#   4. Verifica que se activo (WellbeingService en foreground).
#   5. Lo deja correr 30 s.
#   6. Hace un "like" = un toque en la pantalla (pausa por interaccion).
#   7. Lo deja reanudar y correr otros 30 s.
#   8. Cierra YouTube (Home) para terminar la sesion.
#   9. Verifica que el auto-scroll YA NO este activo.
#
# Requisitos: app instalada y servicio de accesibilidad habilitado
#   (ver scripts/manual_autoscroll_test.ps1 pasos 1-2, o habilitarlo a mano).
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\e2e_youtube_session.ps1
#       (opcional) -Serial emulator-5554

param(
    [string]$Serial = "",
    [int]$RunSeconds = 30
)

$ErrorActionPreference = "Stop"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.freelanzer.autoscroller.debug"

# Coordenadas para ~1080-1280 px de ancho (ajustar si tu pantalla difiere).
$SHORTS_X = 482; $SHORTS_Y = 2700
$SWIPE = @(640, 2200, 640, 600, 250)   # x1 y1 x2 y2 duracion (swipe hacia arriba)
$TAP_X = 640; $TAP_Y = 1400            # toque central (= "like")

function Adb { param([Parameter(ValueFromRemainingArguments=$true)]$a)
    if ($Serial) { & $ADB -s $Serial @a } else { & $ADB @a }
}
function Active {
    # >=1 => auto-scroll activo (WellbeingService en foreground)
    (Adb shell dumpsys activity services $PKG 2>&1 | Select-String "WellbeingService").Count
}

Write-Host "1) Inicio -> abrir YouTube" -ForegroundColor Cyan
Adb shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 1
Adb shell monkey -p com.google.android.youtube -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 6

Write-Host "2) Tocar Shorts" -ForegroundColor Cyan
Adb shell input tap $SHORTS_X $SHORTS_Y
Start-Sleep -Seconds 6

Write-Host "3) 3 swipes hacia arriba (activar)" -ForegroundColor Cyan
for ($i = 1; $i -le 3; $i++) {
    Adb shell input swipe $SWIPE[0] $SWIPE[1] $SWIPE[2] $SWIPE[3] $SWIPE[4]
    Start-Sleep -Milliseconds 800
}
Start-Sleep -Seconds 2

Write-Host "4) Verificar activacion" -ForegroundColor Cyan
Write-Host "   auto-scroll activo: $(Active)  (>=1 = OK)" -ForegroundColor Green

Write-Host "5) Correr $RunSeconds s..." -ForegroundColor Cyan
Start-Sleep -Seconds $RunSeconds

Write-Host "6) Like = un toque en pantalla (pausa por interaccion)" -ForegroundColor Cyan
Adb shell input tap $TAP_X $TAP_Y
Start-Sleep -Seconds 2
Write-Host "   activo tras toque: $(Active)" -ForegroundColor Green

Write-Host "7) Reanudar y correr otros $RunSeconds s..." -ForegroundColor Cyan
Start-Sleep -Seconds $RunSeconds
Write-Host "   activo: $(Active)" -ForegroundColor Green

Write-Host "8) Cerrar YouTube (Home) -> terminar sesion" -ForegroundColor Cyan
Adb shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 3

Write-Host "9) Verificar que NO siga activo" -ForegroundColor Cyan
$final = Active
if ($final -eq 0) {
    Write-Host "   OK: sesion terminada (auto-scroll inactivo)" -ForegroundColor Green
} else {
    Write-Host ("   ATENCION: sigue activo (" + $final + " refs)") -ForegroundColor Red
}

Write-Host ""
Write-Host "Nota: la activacion por swipes requiere que la app emita TYPE_VIEW_SCROLLED." -ForegroundColor DarkYellow
Write-Host "El player fullscreen de YouTube Shorts puede no emitirlos; en ese caso usar el" -ForegroundColor DarkYellow
Write-Host "tap de 3 dedos (device real) como activador. Ver TESTING.md." -ForegroundColor DarkYellow
