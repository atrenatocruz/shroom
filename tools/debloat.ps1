# Disables (never uninstalls) the packages in a list, one per line, # for comments. Every package is
# logged in the backup's changes.log; restore.ps1 re-enables them all.
#
# Usage: tools\debloat.ps1 debloat\my-tv.txt [-TvHost 192.168.1.50]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$TvHost
)
. "$PSScriptRoot\common.ps1"
Use-TvHost $TvHost | Out-Null
$backup = Get-FirstBackup
if (-not $backup) { throw 'Run tools\backup.ps1 first.' }

$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
$log = Join-Path $backup 'changes.log'
foreach ($p in $pkgs) {
    Add-Content -Path $log -Value "$(Get-Date -Format s) disable $p"
    Write-Host ("{0,-60} {1}" -f $p, (Tv "pm disable-user --user 0 $p"))
}
Write-Host "Disabled $($pkgs.Count) package(s). Undo: tools\restore.ps1"
