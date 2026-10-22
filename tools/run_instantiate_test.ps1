$sdkCache = Join-Path $env:USERPROFILE '.stein-sdk\1.8.9'
$modpackRoot = 'C:\Users\artur\Desktop\Modpack'
$toolsDir = Join-Path $modpackRoot 'Sources\COISAS\YNYUtils\tools'
$libraryPaths = Get-Content (Join-Path $sdkCache 'libraries.txt') | ForEach-Object {
    $p = $_
    if (-not (Test-Path $p)) {
        $p = Join-Path $modpackRoot ($p -replace '^\.\.[\\/]\.\.[\\/]', '')
    }
    if (Test-Path $p) { (Resolve-Path -LiteralPath $p).Path }
}
$cp = @(
    $toolsDir,
    (Join-Path $toolsDir 'asm-9.9.jar'),
    (Join-Path $toolsDir 'asm-tree-9.9.jar'),
    (Join-Path $sdkCache 'minecraft-named-open.jar'),
    (Join-Path $sdkCache 'stein-api.jar'),
    (Join-Path $modpackRoot 'data\.minecraft\versions\1.8.9-SteinLoader\1.8.9-SteinLoader.jar'),
    (Join-Path $modpackRoot 'data\.minecraft\mods\YNYUtils.steinmod')
) + $libraryPaths -join ';'

& javac -cp $cp (Join-Path $toolsDir 'TestRealInstantiate.java')
& java -cp $cp TestRealInstantiate
