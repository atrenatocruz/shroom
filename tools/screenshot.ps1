# Saves what the TV shows to screenshots\tv-<time>.png. Optional key presses first, e.g.
#   tools\screenshot.ps1 -Keys DPAD_RIGHT,DPAD_RIGHT
param([string]$TvHost, [string[]]$Keys = @())
. "$PSScriptRoot\common.ps1"
Use-TvHost $TvHost | Out-Null
foreach ($k in ($Keys -join ",").Split(",", [StringSplitOptions]::RemoveEmptyEntries)) { Tv ("input keyevent " + ($k.Trim() -replace "^LONG_", "--longpress KEYCODE_" -replace "^(?!--)", "KEYCODE_")) | Out-Null; Start-Sleep -Milliseconds 350 }
$dir = Join-Path $Repo 'screenshots'
New-Item -ItemType Directory -Force $dir | Out-Null
$file = Join-Path $dir ("tv-{0}.png" -f (Get-Date -Format 'HHmmss'))
Tv 'screencap -p /data/local/tmp/shot.png' | Out-Null
& $Py -I $TvPy --pull /data/local/tmp/shot.png $file | Out-Null
Tv 'rm -f /data/local/tmp/shot.png' | Out-Null
Write-Host $file
