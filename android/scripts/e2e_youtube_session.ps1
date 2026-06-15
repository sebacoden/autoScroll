# E2E de una sesion de auto-scroll en YouTube Shorts (Windows / PowerShell).
# (Texto ASCII a proposito: powershell.exe 5.1 lee .ps1 en ANSI y rompe acentos.)
#
# Escenario verificado (el que pidio el usuario):
#   1. Abre YouTube en el feed de Shorts (video vertical).
#   2. Inicia la sesion de auto-scroll de forma DETERMINISTA via el receiver de debug
#      (broadcast E2E_START con la app atribuida a YouTube). El tap de 3 dedos no es
#      simulable en emulador y la activacion por swipes no es confiable en el player de
#      Shorts; por eso el arranque usa el hook de debug. Ver TESTING.md.
#   3. Corre $RunSeconds s de auto-scroll.
#   4. "Like" en el video (doble-tap real). Si YouTube Shorts NO lo expone como evento de
#      accesibilidad (TYPE_VIEW_CLICKED) -> se usa el hook E2E_INTERACT como fallback, que
#      ejercita EXACTAMENTE el mismo camino de produccion (notifyUserInteraction -> pausa).
#   5. La sesion se PAUSA y se REANUDA sola tras ~3 s sin interaccion (pauseOnTouchSeconds).
#   6. Corre otros $RunSeconds s de auto-scroll.
#   7. Sale de la app (HOME) -> la sesion TERMINA al dejar YouTube.
#   8. Verifica el corte (WellbeingService abajo) y que la sesion quedo registrada en Room.
#
# Toda la verificacion se hace leyendo logcat (tags AutoScrollSvc / AutoScrollEngine, que
# solo loguean en build debug) y la base Room via run-as.
#
# Requisitos: build DEBUG instalada (./gradlew :app:installDebug). El script habilita el
# servicio de accesibilidad por adb si hace falta.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\e2e_youtube_session.ps1
#       (opcional) -Serial emulator-5554  -RunSeconds 30

param(
    [string]$Serial = "",
    [int]$RunSeconds = 30
)

$ErrorActionPreference = "Stop"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.freelanzer.autoscroller.debug"
$SVC = "$PKG/com.freelanzer.autoscroller.service.accessibility.AutoScrollService"
$YT  = "com.google.android.youtube"

# Funcion simple (sin param block) para que TODOS los argumentos caigan en el $args
# automatico y ningun flag de adb (-c, -d, -s, -a, -p...) colisione con un nombre de parametro.
function Adb {
    if ($Serial) { & $ADB -s $Serial @args } else { & $ADB @args }
}
function WellbeingRefs {
    # >=1 => auto-scroll activo (WellbeingService en foreground)
    (Adb shell dumpsys activity services $PKG 2>&1 | Select-String "WellbeingService").Count
}
function Cast { param([string]$action, [string[]]$extra)
    $cmd = @("shell","am","broadcast","-a","com.freelanzer.autoscroller.$action","-p",$PKG)
    if ($extra) { $cmd += $extra }
    Adb @cmd | Out-Null
}
$script:pass = $true
function Check { param([string]$label, [bool]$ok)
    if ($ok) { Write-Host ("   [OK]   " + $label) -ForegroundColor Green }
    else     { Write-Host ("   [FAIL] " + $label) -ForegroundColor Red; $script:pass = $false }
}

# --- 0) Preparacion ------------------------------------------------------------
Write-Host "0) Preparacion (servicio de accesibilidad + screen size)" -ForegroundColor Cyan
$enabled = (Adb shell settings get secure enabled_accessibility_services 2>&1)
if ("$enabled" -notmatch [regex]::Escape($SVC)) {
    Write-Host "   habilitando servicio de accesibilidad..." -ForegroundColor DarkYellow
    Adb shell settings put secure enabled_accessibility_services $SVC | Out-Null
    Adb shell settings put secure accessibility_enabled 1 | Out-Null
    Start-Sleep -Seconds 2
}
$sizeLine = (Adb shell wm size 2>&1 | Select-String "Physical size").ToString()
if ($sizeLine -match "(\d+)x(\d+)") { $W = [int]$Matches[1]; $H = [int]$Matches[2] } else { $W = 1080; $H = 2400 }
$CX = [int]($W / 2); $CY = [int]($H / 2)
Write-Host "   pantalla ${W}x${H}, centro ($CX,$CY)" -ForegroundColor Gray

Adb logcat -c
Adb shell am force-stop $YT 2>&1 | Out-Null

# --- 1) Abrir YouTube Shorts ---------------------------------------------------
Write-Host "1) Abrir YouTube Shorts" -ForegroundColor Cyan
Adb shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/shorts" 2>&1 | Out-Null
Start-Sleep -Seconds 8

# --- 2) Iniciar sesion (hook de debug, atribuida a YouTube) --------------------
Write-Host "2) Iniciar auto-scroll (broadcast E2E_START, pkg=YouTube)" -ForegroundColor Cyan
Cast "E2E_START" @("--es","pkg",$YT)
Start-Sleep -Seconds 3
Check "WellbeingService activo tras START" ((WellbeingRefs) -ge 1)

# --- 3) Correr fase 1 ----------------------------------------------------------
Write-Host "3) Auto-scroll por $RunSeconds s (fase 1)..." -ForegroundColor Cyan
Start-Sleep -Seconds $RunSeconds

# --- 4) Like (doble-tap real; fallback al hook si no se detecta) ---------------
Write-Host "4) Like en el video (doble-tap real)" -ForegroundColor Cyan
$before = (Adb logcat -d -s AutoScrollSvc:D 2>&1 | Select-String "detectada").Count
Adb shell input tap $CX $CY | Out-Null
Start-Sleep -Milliseconds 180
Adb shell input tap $CX $CY | Out-Null
Start-Sleep -Seconds 2
$after = (Adb logcat -d -s AutoScrollSvc:D 2>&1 | Select-String "detectada").Count
if ($after -gt $before) {
    Write-Host "   like REAL detectado como evento de accesibilidad (pausa por interaccion)" -ForegroundColor Green
    $likeMode = "real"
} else {
    Write-Host "   el player de Shorts no expuso el like como TYPE_VIEW_CLICKED" -ForegroundColor DarkYellow
    Write-Host "   -> fallback determinista: broadcast E2E_INTERACT (mismo camino de produccion)" -ForegroundColor DarkYellow
    Cast "E2E_INTERACT"
    $likeMode = "hook"
}

# --- 5) Verificar pausa + reanudacion ~3 s -------------------------------------
Write-Host "5) Esperando reanudacion (~3 s sin interaccion)..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

# --- 6) Correr fase 2 ----------------------------------------------------------
Write-Host "6) Auto-scroll por $RunSeconds s (fase 2)..." -ForegroundColor Cyan
Start-Sleep -Seconds $RunSeconds

# --- 7) Salir de la app -> terminar sesion -------------------------------------
Write-Host "7) HOME (salir de YouTube) -> terminar sesion" -ForegroundColor Cyan
Adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 3
Check "WellbeingService inactivo tras salir" ((WellbeingRefs) -eq 0)

# --- 8) Verificacion final por logcat ------------------------------------------
Write-Host "8) Verificacion por logcat" -ForegroundColor Cyan
$log = Adb logcat -d -s AutoScrollSvc:D AutoScrollEngine:D 2>&1
function Has { param([string]$pat) ($log | Select-String -SimpleMatch $pat).Count -ge 1 }

Check "sesion iniciada en YouTube"        (($log | Select-String "INICIADA en '$YT'").Count -ge 1)
$swipes1 = ($log | Select-String "swipe #").Count
Check "hubo swipes de auto-scroll"        ($swipes1 -ge 2)
Check "pausa por interaccion"             (Has "PAUSADO por")
Check "reanudacion automatica"            (Has "REANUDADO tras")
Check "sesion terminada al salir"         (Has "TERMINADA")
Check "corte por salir de la app"         (Has "deteniendo")

# Delta pausa->reanudacion (best-effort, debe rondar pauseOnTouchSeconds = 3 s)
$pa = ($log | Select-String "PAUSADO por"   | Select-Object -First 1)
$re = ($log | Select-String "REANUDADO tras"| Select-Object -First 1)
if ($pa -and $re) {
    function Stamp($line) { if ("$line" -match "(\d\d):(\d\d):(\d\d)\.(\d\d\d)") {
        return ([int]$Matches[1]*3600 + [int]$Matches[2]*60 + [int]$Matches[3]) * 1000 + [int]$Matches[4] } return $null }
    $d = (Stamp $re) - (Stamp $pa)
    if ($d -ne $null) { Write-Host ("   pausa -> reanudacion: {0} ms (esperado ~3000)" -f $d) -ForegroundColor Gray }
}

# --- 9) Verificar registro en Room ---------------------------------------------
# El emulador no trae el binario sqlite3, asi que confirmamos la escritura real con el
# log de RoomUsageRepository (tag AutoScrollUsage, solo debug) emitido tras dao.insert().
Write-Host "9) Sesion registrada en Room (autoscroller_usage.db)" -ForegroundColor Cyan
$usage = Adb logcat -d -s AutoScrollUsage:D 2>&1
$row = ($usage | Select-String "sesion persistida" | Select-Object -Last 1)
Write-Host ("   " + ("$row").Trim()) -ForegroundColor Gray
Check "fila Room con la app YouTube" ("$row" -match "pkg=$([regex]::Escape($YT))")
Check "fila Room con swipeCount > 0" ("$row" -match "swipes=([1-9]\d*)")

# --- Resumen -------------------------------------------------------------------
Write-Host ""
Write-Host ("Modo de like: " + $likeMode) -ForegroundColor Gray
if ($script:pass) { Write-Host "RESULTADO: PASS" -ForegroundColor Green }
else              { Write-Host "RESULTADO: FAIL (ver [FAIL] arriba)" -ForegroundColor Red; exit 1 }
