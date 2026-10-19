# One-time setup on this PC: the Python ADB client used by every other script.
# Usage: powershell -ExecutionPolicy Bypass -File tools\setup.ps1
$ErrorActionPreference = 'Stop'
$Repo = Split-Path -Parent $PSScriptRoot
$venv = Join-Path $Repo 'tools\venv'
if (-not (Test-Path "$venv\Scripts\python.exe")) { python -m venv $venv }
& "$venv\Scripts\python.exe" -m pip install -q -r (Join-Path $Repo 'tools\requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'pip install failed' }
Write-Host 'Tools ready. Next: tools\connect.ps1 -TvHost <your TV IP>'
