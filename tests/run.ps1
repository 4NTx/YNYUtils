param([string]$Jdk = 'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $sdkCache = Join-Path $env:USERPROFILE '.stein-sdk\1.8.9'
    $modpackRoot = 'C:\Users\artur\Desktop\Modpack'
    $libraryPaths = Get-Content (Join-Path $sdkCache 'libraries.txt') | ForEach-Object {
        $p = $_
        if (-not (Test-Path $p)) {
            $p = Join-Path $modpackRoot ($p -replace '^\.\.[\\/]\.\.[\\/]', '')
        }
        if (Test-Path $p) { (Resolve-Path -LiteralPath $p).Path }
    }
    $compileClassPath = @((Join-Path $sdkCache 'minecraft-named-open.jar'),
        (Join-Path $sdkCache 'stein-api.jar'), (Resolve-Path 'build\stein\classes').Path) + $libraryPaths -join ';'
    $packJar = (Resolve-Path 'build\YNYUtils.steinmod').Path
    $teamJar = (Resolve-Path 'team-source\build\libs\YNYUtilsEquipe.steinmod').Path
    $combatJar = (Resolve-Path '..\SteinCombat\build\libs\Combat.steinmod').Path
    $toolsDir = (Resolve-Path 'tools').Path

    New-Item -ItemType Directory -Path 'build\tests' -Force | Out-Null
    & "$Jdk\bin\javac.exe" -encoding UTF-8 -classpath "$compileClassPath;$teamJar;$combatJar" -d build\tests tests\AutoArmorSafetyTest.java tests\KnockbackControlTest.java tests\ConsumablePolicyTest.java tests\AutoProtectTest.java tests\BowAimAssistTest.java tests\BrokenArmorHighlightTest.java tests\AdvancedClanDetectionTest.java tests\FocusModeAndSelfArmorHudTest.java tests\AllyAttackFlowTest.java tests\VerifyAllTaskFixesTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Falha compilando testes' }
    $testsPath = (Resolve-Path 'build\tests').Path
    Push-Location $testsPath
    try {
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" com.yny.utils.modules.player.AutoArmorSafetyTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Auto Armor detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" com.yny.utils.modules.pvp.KnockbackControlTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Knockback detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" com.yny.utils.modules.player.ConsumablePolicyTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Auto Consumíveis detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" tests.AutoProtectTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Auto Protect detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" tests.BowAimAssistTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Bow Aim Assist detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" com.yny.utils.modules.pvp.BrokenArmorHighlightTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Broken Armor Highlight detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" tests.AdvancedClanDetectionTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Advanced Clan Detection detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" com.yny.utils.modules.pvp.FocusModeAndSelfArmorHudTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão FocusMode e SelfArmorHud detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$compileClassPath" com.yny.utils.modules.pvp.AllyAttackFlowTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão Ally Attack Flow detectada' }
        & "$Jdk\bin\java.exe" -classpath "$testsPath;$packJar;$combatJar;$teamJar;$toolsDir;$compileClassPath" com.yny.utils.modules.pvp.VerifyAllTaskFixesTest
        if ($LASTEXITCODE -ne 0) { throw 'Regressão VerifyAllTaskFixes detectada' }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}
