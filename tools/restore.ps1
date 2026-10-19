# Puts the TV back the way the backup found it: re-enables every package that was enabled then and is
# disabled now, gives Home back to the original launcher, and optionally removes Shroom.
#
# Usage: tools\restore.ps1 [-TvHost 192.168.1.50] [-Backup backups\<folder>] [-Uninstall]
#   Uses the OLDEST backup by default, i.e. the state before Shroom was ever installed.
param(
    [string]$TvHost,
    [string]$Backup,
    [switch]$Uninstall
)
. "$PSScriptRoot\common.ps1"
Use-TvHost $TvHost | Out-Null

if (-not $Backup) { $Backup = Get-FirstBackup }
if (-not $Backup -or -not (Test-Path (Join-Path $Backup 'disabled.txt'))) { throw 'No backup found in backups\.' }
Write-Host "Restoring to $Backup"

$then = @(Get-Content (Join-Path $Backup 'disabled.txt') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$now = @(Get-DisabledPackages)
$toEnable = @($now | Where-Object { $then -notcontains $_ })
$homeBefore = (Get-Content (Join-Path $Backup 'home.txt') -Raw).Trim()

$cmds = @($toEnable | ForEach-Object { "pm enable $_" })
if ($homeBefore -and $homeBefore -ne 'android' -and $homeBefore -notlike "$ShroomPkg/*") {
    $cmds += "cmd package set-home-activity --user 0 $homeBefore"
}
# Accessibility services back to what the backup had (drops Shroom's Home helper).
$acc = Get-Content (Join-Path $Backup 'settings-secure.txt') | Where-Object { $_ -like 'enabled_accessibility_services=*' } | Select-Object -First 1
if ($acc -and $acc.Substring(31)) { $cmds += "settings put secure enabled_accessibility_services $($acc.Substring(31))" }
else { $cmds += 'settings delete secure enabled_accessibility_services' }
if ($Uninstall) { $cmds += "pm uninstall $ShroomPkg" }
if ($cmds.Count -gt 0) { TvBatch $cmds | Write-Host }
Tv 'input keyevent KEYCODE_HOME' | Out-Null

Write-Host "Re-enabled $($toEnable.Count) package(s). Home is now: $(Get-HomeComponent)" -ForegroundColor Green
if (-not $Uninstall) { Write-Host 'Shroom is still installed (open it from the app list, or re-run with -Uninstall).' }
