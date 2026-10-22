param(
    [Parameter(Mandatory=$true)][string]$Base,
    [Parameter(Mandatory=$true)][string]$Team,
    [Parameter(Mandatory=$true)][string]$Output
)
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $Output) { throw "Output already exists: $Output" }
$baseZip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Base))
$teamZip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Team))
$outZip = [System.IO.Compression.ZipFile]::Open($Output, [System.IO.Compression.ZipArchiveMode]::Create)
$names = [System.Collections.Generic.HashSet[string]]::new()
function Copy-Entry($entry) {
    if (-not $names.Add($entry.FullName)) { throw "Duplicate entry: $($entry.FullName)" }
    $dest = $outZip.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
    $from = $entry.Open()
    $to = $dest.Open()
    try { $from.CopyTo($to) } finally { $to.Dispose(); $from.Dispose() }
}
function Write-TextEntry([string]$name, [string]$value) {
    if (-not $names.Add($name)) { throw "Duplicate entry: $name" }
    $entry = $outZip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
    $writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
    try { $writer.Write($value) } finally { $writer.Dispose() }
}
try {
    foreach ($entry in $baseZip.Entries) {
        if ($entry.FullName -eq "steinmod.json" -or $entry.FullName -eq "META-INF/services/dev.xavier.stein.loader.api.SteinMod") { continue }
        Copy-Entry $entry
    }
    foreach ($entry in $teamZip.Entries) {
        $name = $entry.FullName
        if (($name.StartsWith("com/yny/utils/team/") -and $name.EndsWith(".class")) -or
            ($name.StartsWith("assets/steincombat/lang/") -and $name.EndsWith(".lang")) -or
            ($name -eq "assets/steincombat/defaults.json")) {
            Copy-Entry $entry
        }
    }
    $manifestEntry = $baseZip.GetEntry("steinmod.json")
    $reader = [System.IO.StreamReader]::new($manifestEntry.Open())
    try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    $manifest.entry = @("com.yny.utils.YNYUtils", "com.yny.utils.team.SteinCombat")
    $manifest.loader = "0.1.25"
    Write-TextEntry "steinmod.json" ($manifest | ConvertTo-Json -Depth 8)
    Write-TextEntry "META-INF/services/dev.xavier.stein.loader.api.SteinMod" "com.yny.utils.YNYUtils`ncom.yny.utils.team.SteinCombat`n"
    $teamSource = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent (Resolve-Path -LiteralPath $Team)))
    Write-TextEntry "META-INF/licenses/SteinCombat-LICENSE" ([System.IO.File]::ReadAllText((Join-Path $teamSource "LICENSE")))
    Write-TextEntry "META-INF/licenses/SteinCombat-NOTICE" ([System.IO.File]::ReadAllText((Join-Path $teamSource "NOTICE")))
} finally {
    $outZip.Dispose()
    $teamZip.Dispose()
    $baseZip.Dispose()
}