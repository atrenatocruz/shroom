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
$ok = 0
foreach ($p in $pkgs) {
    Add-Content -Path $log -Value "$(Get-Date -Format s) disable $p"
    $out = Tv "pm disable-user --user 0 $p"
    if ($out -match 'disabled-user') { $ok++; Write-Host ("{0,-55} off" -f $p) }
    elseif ($out -match 'protected') { Write-Host ("{0,-55} protected by the TV, left on" -f $p) -ForegroundColor DarkGray }
    else { Write-Host ("{0,-55} {1}" -f $p, ($out -split "`n")[0]) -ForegroundColor Yellow }
}
Write-Host "Switched off $ok of $($pkgs.Count). Undo: tools\restore.ps1"
