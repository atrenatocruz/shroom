# Shared helpers, dot-sourced by the other scripts: . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; Windows PowerShell's default BOM corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

$Repo = Split-Path -Parent $PSScriptRoot
$Py = Join-Path $Repo 'tools\venv\Scripts\python.exe'
$TvPy = Join-Path $Repo 'tools\tv.py'
$Backups = Join-Path $Repo 'backups'
$HostFile = Join-Path $Repo 'tools\.tvhost'
$ShroomPkg = 'app.shroom.tv'
$ShroomHome = 'app.shroom.tv/.MainActivity'
# Launchers the scripts know how to hand Home back and forth with.
$KnownStock = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher')

if (-not (Test-Path $Py)) {
    throw "Python tools not set up yet. Run: powershell -ExecutionPolicy Bypass -File tools\setup.ps1"
}

# -TvHost 192.168.1.50 (or 192.168.1.50:5555) is remembered in tools\.tvhost for the next script.
function Use-TvHost([string]$TvHost) {
    if (-not $TvHost -and (Test-Path $HostFile)) { $TvHost = (Get-Content $HostFile -Raw).Trim() }
    if (-not $TvHost) { throw 'Pass -TvHost <your TV IP> (Settings > Network on the TV shows it).' }
    Set-Content -Path $HostFile -Value $TvHost -Encoding ascii
    $parts = $TvHost.Split(':')
    $env:TV_HOST = $parts[0]
    $env:TV_PORT = if ($parts.Count -gt 1) { $parts[1] } else { '5555' }
    return $TvHost
}

# One shell command on the TV; returns its output as one string.
function Tv([string]$Command) {
    $out = & $Py -I $TvPy $Command
    if ($LASTEXITCODE -ne 0) { throw "Could not reach the TV ($env:TV_HOST). Is it on, and was 'Allow USB debugging' accepted?" }
    return (($out | ForEach-Object { "$_" }) -join "`n").Trim()
}

# Several commands over one connection, each in its own shell call (chained pm commands can stop early).
function TvBatch([string[]]$Commands) {
    $out = $Commands | & $Py -I $TvPy --batch
    if ($LASTEXITCODE -ne 0) { throw "Could not reach the TV ($env:TV_HOST)." }
    return ($out | ForEach-Object { "$_" })
}

function Get-HomeComponent {
    $r = Tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
    return ($r -split "`n" | Select-Object -Last 1).Trim()
}

function Get-DisabledPackages {
    return (Tv 'pm list packages -d') -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ }
}

# The first complete backup: the TV as it was before Shroom touched it.
function Get-FirstBackup {
    if (-not (Test-Path $Backups)) { return $null }
    $d = Get-ChildItem $Backups -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'disabled.txt') } |
        Sort-Object Name | Select-Object -First 1
    if ($d) { return $d.FullName } else { return $null }
}
