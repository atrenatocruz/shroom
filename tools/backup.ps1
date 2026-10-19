# Snapshot of the TV before anything changes: packages (all / disabled / third-party), the Home app,
# settings and device properties, in backups\<date-time>\. restore.ps1 rolls the TV back to it.
# Usage: tools\backup.ps1 [-TvHost 192.168.1.50]
param([string]$TvHost)
. "$PSScriptRoot\common.ps1"
Use-TvHost $TvHost | Out-Null

$dir = Join-Path $Backups (Get-Date -Format 'yyyy-MM-dd_HHmmss')
New-Item -ItemType Directory -Force $dir | Out-Null
function Save([string]$name, [string]$cmd) {
    Set-Content -Path (Join-Path $dir $name) -Value (Tv $cmd) -Encoding utf8
}
Save 'packages.txt' 'pm list packages -f'
Save 'third-party.txt' 'pm list packages -3'
Save 'getprop.txt' 'getprop'
Save 'settings-global.txt' 'settings list global'
Save 'settings-secure.txt' 'settings list secure'
Save 'settings-system.txt' 'settings list system'
Save 'home-apps.txt' 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
Set-Content -Path (Join-Path $dir 'home.txt') -Value (Get-HomeComponent) -Encoding ascii
# Written last: its presence marks the backup complete.
Set-Content -Path (Join-Path $dir 'disabled.txt') -Value ((Get-DisabledPackages) -join "`n") -Encoding ascii

$count = ((Get-Content (Join-Path $dir 'packages.txt')) | Measure-Object -Line).Lines
Write-Host "Backup saved to $dir ($count packages, Home = $(Get-Content (Join-Path $dir 'home.txt')))"
