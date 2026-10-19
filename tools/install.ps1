# Builds Shroom, installs it on the TV and makes it the Home screen - without touching anything else
# unless you pass -DisableStock. Takes a backup first if there is none.
#
# Usage: tools\install.ps1 [-TvHost 192.168.1.50] [-SkipBuild] [-DisableStock] [-StockLauncher <package>]
#                           [-AlsoDisable com.google.android.tungsten.setupwraith]
#   -DisableStock   needed on most Google TV sets, where the stock launcher keeps Home otherwise.
#                   Rolled back automatically if Home does not land on Shroom.
#   -AlsoDisable    other packages that grab Home (Google TV's setup wizard), disabled with the stock one.
param(
    [string]$TvHost,
    [switch]$SkipBuild,
    [switch]$DisableStock,
    [string]$StockLauncher = '',
    [string[]]$AlsoDisable = @()
)
. "$PSScriptRoot\common.ps1"
Use-TvHost $TvHost | Out-Null
$apk = Join-Path $Repo 'launcher\app\build\outputs\apk\release\app-release.apk'

# 1. Build
if (-not $SkipBuild) {
    & "$PSScriptRoot\build.ps1"
}
if (-not (Test-Path $apk)) { throw "No APK at $apk - run without -SkipBuild." }

# 2. Backup (only the first time: the earliest backup is the factory-ish state to return to)
$backup = Get-FirstBackup
if (-not $backup) {
    & "$PSScriptRoot\backup.ps1"
    $backup = Get-FirstBackup
}
$homeBefore = (Get-Content (Join-Path $backup 'home.txt') -Raw).Trim()
Write-Host "Backup: $backup (Home was $homeBefore)"

# 3. Install and start it once by hand, before Home is touched
Write-Host 'Uploading...'
& $Py -I (Join-Path $PSScriptRoot 'b64push.py') $apk /data/local/tmp/shroom.apk
if ($LASTEXITCODE -ne 0) { throw 'Upload failed; nothing on the TV was changed.' }
$out = Tv 'pm install -r /data/local/tmp/shroom.apk; rm -f /data/local/tmp/shroom.apk'
Write-Host $out
if ($out -notmatch 'Success') { throw 'pm install failed; nothing else on the TV was changed.' }
TvBatch @(
    "cmd package compile -m speed -f $ShroomPkg",
    "am start -W -n $ShroomHome"
) | Out-Null

# 4. Ask Android to make it Home. On many Android TV sets this alone is enough.
Tv "cmd package set-home-activity --user 0 $ShroomHome" | Out-Null
$now = Get-HomeComponent
if ($now -like "$ShroomPkg/*") {
    Write-Host "Done. Home is Shroom." -ForegroundColor Green
    exit 0
}

# 5. The stock launcher still wins: it has to be disabled (not uninstalled) to free the Home slot.
$stock = $StockLauncher
if (-not $stock) { $stock = $now.Split('/')[0] }
if (-not $DisableStock) {
    Write-Host "Shroom is installed, but Home still goes to '$now'." -ForegroundColor Yellow
    Write-Host "Re-run with -SkipBuild -DisableStock to switch it off (restore.ps1 switches it back on)."
    exit 2
}
if ($KnownStock -notcontains $stock) {
    throw "Home resolves to '$now', which is not a launcher this script recognises. Pass -StockLauncher <package> if you are sure."
}
$extra = @(($AlsoDisable -join ',').Split(',', [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $_.Trim() })
foreach ($p in $extra + $stock) {
    Write-Host "Disabling $p..."
    Add-Content -Path (Join-Path $backup 'changes.log') -Value "$(Get-Date -Format s) disable $p"
    Tv "pm disable-user --user 0 $p" | Write-Host
}
Tv "cmd package set-home-activity --user 0 $ShroomHome" | Out-Null
Start-Sleep -Seconds 2
$now = Get-HomeComponent
if ($now -like "$ShroomPkg/*") {
    Write-Host "Done. Home is Shroom; $stock is disabled (restore.ps1 brings it back)." -ForegroundColor Green
    exit 0
}

# 6. Something else grabbed Home (Google TV's setup wizard does): undo, never leave the TV homeless.
foreach ($p in @($stock) + $extra) { Tv "pm enable $p" | Out-Null }
Tv "cmd package set-home-activity --user 0 $homeBefore" | Out-Null
Add-Content -Path (Join-Path $backup 'changes.log') -Value "$(Get-Date -Format s) re-enabled $stock $extra (rollback)"
throw "Home went to '$now' instead of Shroom, so everything was switched back on. If that is com.google.android.tungsten.setupwraith (Google TV's setup wizard), re-run with -AlsoDisable com.google.android.tungsten.setupwraith."
