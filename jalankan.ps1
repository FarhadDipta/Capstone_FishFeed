$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Cari JDK 17-21 (Gradle 8.7 tidak mendukung Java 22+)
$roots = @("$env:USERPROFILE\.jdks","C:\Program Files\Microsoft","C:\Program Files\Eclipse Adoptium","C:\Program Files\Java","C:\Program Files\Zulu","C:\Program Files\Android","$env:LOCALAPPDATA\Programs")
$jdk = $null
foreach ($r in $roots) {
  if (-not (Test-Path $r)) { continue }
  $exes = Get-ChildItem $r -Recurse -Depth 3 -Filter java.exe -ErrorAction SilentlyContinue
  foreach ($e in $exes) {
    $major = $e.VersionInfo.FileMajorPart
    if ($major -ge 17 -and $major -le 21) { $jdk = $e.Directory.Parent.FullName; break }
  }
  if ($jdk) { break }
}
if (-not $jdk) { Write-Host "Java 17-21 tidak ditemukan. Pasang dengan perintah:  winget install Microsoft.OpenJDK.21" -ForegroundColor Red; exit 1 }
$env:JAVA_HOME = $jdk
Write-Host "Memakai Java: $jdk" -ForegroundColor Green

# Lokasi Android SDK -> local.properties (dibuat otomatis)
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
if (-not (Test-Path "local.properties")) {
  if (Test-Path $sdk) { ("sdk.dir=" + ($sdk -replace '\\','/')) | Set-Content -Encoding ascii "local.properties" }
  else { Write-Host "Android SDK tidak ditemukan di $sdk. Pasang Android Studio dulu." -ForegroundColor Red; exit 1 }
}

& .\gradlew.bat --stop | Out-Null
& .\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$adb = Join-Path $sdk "platform-tools\adb.exe"
& $adb shell am start -n com.fishfeed/.MainActivity
