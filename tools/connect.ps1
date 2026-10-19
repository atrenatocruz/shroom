# Connects to the TV and prints what it is. The first time, the TV shows "Allow USB debugging?":
# tick "Always allow from this computer" and press OK. Remembers the IP for the other scripts.
# Usage: tools\connect.ps1 -TvHost 192.168.1.50
param([string]$TvHost)
. "$PSScriptRoot\common.ps1"
$h = Use-TvHost $TvHost
Write-Host "Connecting to $h - accept the prompt on the TV if it appears (you have 2 minutes)..."
& $Py -I $TvPy --probe
if ($LASTEXITCODE -ne 0) { throw 'Connection failed. Check the IP, that USB/network debugging is on, and that the TV is awake.' }
Write-Host "Home is currently: $(Get-HomeComponent)"
