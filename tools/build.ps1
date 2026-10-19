# Builds launcher\app\build\outputs\apk\release\app-release.apk (about 600 KB).
# Needs JDK 17 and the Android SDK (platform 34): JAVA_HOME / ANDROID_HOME, or the usual install paths.
$ErrorActionPreference = 'Stop'
$Repo = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $Repo 'launcher'

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdk = @(Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue) +
        @(Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue | ForEach-Object { Get-Item (Join-Path $_.FullName 'jbr') -ErrorAction SilentlyContinue }) |
        Where-Object { $_ -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } | Select-Object -First 1
    if (-not $jdk) { throw 'JDK 17 not found: winget install Microsoft.OpenJDK.17' }
    $env:JAVA_HOME = $jdk.FullName
}
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path "$sdk\platforms\android-34")) { throw "Android SDK platform 34 not found in $sdk" }
Set-Content -Path (Join-Path $launcher 'local.properties') -Value ("sdk.dir=" + ($sdk -replace '\\', '/' -replace ':', '\:')) -Encoding ascii
# Java's internal loopback pipe is an AF_UNIX socket in a temp folder; on Windows it fails when that
# path has a space or an 8.3 short name, as user folders like "C:\Users\First Last" do.
$tmp = New-Item -ItemType Directory -Force (Join-Path $env:ProgramData 'shroom-jdk')
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$($tmp.FullName)"

Push-Location $launcher
$ErrorActionPreference = 'Continue'   # Gradle and Java chat on stderr; judge by the exit code instead
try {
    & .\gradlew.bat assembleRelease --console=plain -q 2>&1 | ForEach-Object { "$_" } |
        Where-Object { $_ -notmatch 'JAVA_TOOL_OPTIONS|RemoteException' -and $_ }
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
} finally {
    Pop-Location
    $ErrorActionPreference = 'Stop'
}
$apk = Join-Path $launcher 'app\build\outputs\apk\release\app-release.apk'
Write-Host "Built $apk ($([math]::Round((Get-Item $apk).Length / 1KB)) KB)"
