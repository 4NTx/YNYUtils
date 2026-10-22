param([string]$Jdk = 'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $sdkCache = Join-Path $env:USERPROFILE '.stein-sdk\1.8.9'
    $cp = @(
        (Resolve-Path 'artifacts\YNYUtils.steinmod').Path,
        (Join-Path $sdkCache 'stein-api.jar'),
        (Resolve-Path 'build\tests').Path
    ) -join ';'

    & "$Jdk\bin\javac.exe" --release 8 -encoding UTF-8 -classpath $cp -d build\tests tests\MobCombatTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Falha compilando MobCombatTest' }

    & "$Jdk\bin\java.exe" -Xverify:all -classpath $cp com.yny.utils.modules.pvp.MobCombatTest
    if ($LASTEXITCODE -ne 0) { throw 'Falha executando MobCombatTest' }
} finally {
    Pop-Location
}
