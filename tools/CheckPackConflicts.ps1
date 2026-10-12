$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$gameDirs = @(
    (Join-Path $workspace 'data\.minecraft'),
    (Join-Path $workspace 'COISAS\YNY-Modpack-Limpa\data\.minecraft'),
    (Join-Path $workspace 'COISAS\YNY-Modpack-Limpa\data\data\.minecraft')
)
$hashes = @()
foreach ($gameDir in $gameDirs) {
    $combat = Get-Content -LiteralPath (Join-Path $gameDir 'config\steincombat.json') -Raw | ConvertFrom-Json
    $yny = Get-Content -LiteralPath (Join-Path $gameDir 'config\ynyutils.json') -Raw | ConvertFrom-Json
    $damage = Get-Content -LiteralPath (Join-Path $gameDir 'config\ynydamageindicator.json') -Raw | ConvertFrom-Json
    $hud = Get-Content -LiteralPath (Join-Path $gameDir 'config\steinloader-hud.json') -Raw | ConvertFrom-Json
    $enabled = @($combat.autoArmor, $combat.clanHitThrough, $combat.autoProtect, $combat.protectDefend)
    if ($enabled -contains $true) { throw "Conflicting Combat feature enabled: $gameDir" }
    if (!$yny.autoArmorEnabled -or !$yny.autoProtectEnabled -or !$yny.hitThroughAlliesEnabled -or !$yny.pvpDiagnosticsEnabled -or
        !$yny.inventoryMoveSuspendUse -or !$yny.clanPingHudEnabled -or !$damage.enabled -or
        !$hud.elements.'ynyutils.clanping'.enabled -or !$hud.elements.'ynydamageindicator.target'.enabled) {
        throw "YNYUtils feature or HUD disabled: $gameDir"
    }
    $hashes += (Get-FileHash -LiteralPath (Join-Path $gameDir 'mods\YNYUtils.steinmod') -Algorithm SHA256).Hash
    Write-Output "PASS: $(Split-Path $gameDir -Parent) - YNYUtils enabled, Combat conflicts off"
}
if (($hashes | Select-Object -Unique).Count -ne 1) { throw 'YNYUtils packages differ between game folders' }
$artifactHash = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot '..\artifacts\YNYUtils.steinmod') -Algorithm SHA256).Hash
$buildHash = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot '..\build\YNYUtils.steinmod') -Algorithm SHA256).Hash
if ($artifactHash -ne $hashes[0] -or $buildHash -ne $hashes[0]) {
    throw 'Installed package, artifacts/, and build/ have different hashes'
}
Write-Output "PASS: installed YNYUtils SHA256=$($hashes[0])"
