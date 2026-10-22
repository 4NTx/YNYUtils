$ErrorActionPreference = 'Stop'
$rootDir = $PSScriptRoot
if (-not $rootDir) { $rootDir = (Get-Location).Path }

$sdk = 'C:\Users\artur\.stein-sdk\1.8.9'
$mcNamed = Join-Path $sdk 'minecraft-named-open.jar'
$steinApi = Join-Path $sdk 'stein-api.jar'
$teamMod = (Resolve-Path (Join-Path $rootDir 'team-source/build/libs/YNYUtilsEquipe.steinmod')).Path
$baseMod = (Resolve-Path (Join-Path $rootDir 'artifacts/YNYUtils.steinmod')).Path

$libLines = Get-Content (Join-Path $sdk 'libraries.txt')
$libs = @()
foreach ($line in $libLines) {
    $p = $line
    if (-not (Test-Path $p)) {
        $p = "C:\Users\artur\Desktop\Modpack\" + ($p -replace '^\.\.[\\/]\.\.[\\/]', '')
    }
    if (Test-Path $p) {
        $libs += (Resolve-Path $p).Path
    }
}

$cp = @($mcNamed, $steinApi, $teamMod, $baseMod) + $libs -join ';'

$outDir = Join-Path $rootDir 'build/compiled'
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$srcDir = Join-Path $rootDir 'src/main/java'
$srcFiles = Get-ChildItem -Path $srcDir -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName

Write-Host "Compiling $($srcFiles.Count) source files with --release 8..."
& javac --release 8 -encoding UTF-8 -cp $cp -d $outDir $srcFiles
if ($LASTEXITCODE -ne 0) {
    Write-Error "javac failed with exit code $LASTEXITCODE"
} else {
    Write-Host "Compilation SUCCESSFUL!"

    # Sync to build/stein/classes so test suites run against the latest compiled classes
    $steinClasses = Join-Path $rootDir 'build/stein/classes'
    if (Test-Path $steinClasses) {
        Copy-Item -Path (Join-Path $outDir '*') -Destination $steinClasses -Recurse -Force
        Write-Host "Synced build/compiled -> build/stein/classes"
    }
}
