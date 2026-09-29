param([string]$Jdk = 'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $sdkCache = Join-Path $env:USERPROFILE '.stein-sdk\1.8.9'
    $libraryPaths = Get-Content (Join-Path $sdkCache 'libraries.txt') | ForEach-Object {
        (Resolve-Path -LiteralPath $_).Path
    }
    $classPath = @((Join-Path $sdkCache 'minecraft-named-open.jar'),
        (Join-Path $sdkCache 'stein-api.jar'), (Resolve-Path 'build\stein\classes').Path) + $libraryPaths
    $classPath = $classPath -join ';'
    New-Item -ItemType Directory -Path 'build\tests' -Force | Out-Null
    & "$Jdk\bin\javac.exe" -encoding UTF-8 -classpath $classPath -d build\tests tests\ReachRegressionTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Falha compilando testes' }
    $testsPath = (Resolve-Path 'build\tests').Path
    Push-Location $testsPath
    try {
        & "$Jdk\bin\java.exe" --sun-misc-unsafe-memory-access=allow -classpath "$testsPath;$classPath" com.yny.utils.modules.pvp.ReachRegressionTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão detectada' }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}
