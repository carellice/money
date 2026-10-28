param([ValidateSet("patch", "minor", "major")][string]$Bump = "patch")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Magenta }
function Fail($text) { Write-Host "`nERRORE: $text" -ForegroundColor Red; exit 1 }
function Run($exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { Fail "$exe $($arguments -join ' ') non riuscito" }
}

# --- controlli ---------------------------------------------------------------------------------
Step "Controlli"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail "GitHub CLI (gh) non trovata" }
cmd /c "gh auth status >nul 2>nul"
if ($LASTEXITCODE -ne 0) { Fail "GitHub CLI non autenticata: esegui 'gh auth login'" }
if (-not (Test-Path "signing\keystore.properties")) { Fail "Manca signing\keystore.properties: senza la chiave di release gli aggiornamenti non si installano" }

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$dirty = git status --porcelain
if ($dirty) {
    Write-Host "Ci sono modifiche non salvate che finiranno nella release:" -ForegroundColor Yellow
    $dirty | ForEach-Object { Write-Host "  $_" }
    $answer = Read-Host "Continuare? (s/N)"
    if ($answer -notmatch '^[sSyY]') { Fail "Annullato" }
}

# --- versione ----------------------------------------------------------------------------------
Step "Nuova versione"
$versionFile = Join-Path $root "version.properties"
$props = @{}
Get-Content $versionFile | Where-Object { $_ -match '=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $props[$k.Trim()] = $v.Trim()
}
$oldName = $props["VERSION_NAME"]
$parts = $oldName.Split('.') | ForEach-Object { [int]$_ }
while ($parts.Count -lt 3) { $parts += 0 }
switch ($Bump) {
    "major" { $parts = @($parts[0] + 1, 0, 0) }
    "minor" { $parts = @($parts[0], $parts[1] + 1, 0) }
    default { $parts = @($parts[0], $parts[1], $parts[2] + 1) }
}
$newName = $parts -join '.'
$newCode = [int]$props["VERSION_CODE"] + 1
$tag = "v$newName"
if (git tag --list $tag) { Fail "Il tag $tag esiste gia'" }
Write-Host "$oldName -> $newName (versionCode $newCode)"

$original = Get-Content $versionFile -Raw
[IO.File]::WriteAllText($versionFile, "VERSION_NAME=$newName`nVERSION_CODE=$newCode`n")

# --- build -------------------------------------------------------------------------------------
Step "Compilo l'APK firmato"
& "$root\gradlew.bat" :app:assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) {
    [IO.File]::WriteAllText($versionFile, $original)
    Fail "Build non riuscita, versione ripristinata a $oldName"
}
$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
$outDir = Join-Path $root "build\release"
New-Item -ItemType Directory -Force $outDir | Out-Null
$asset = Join-Path $outDir "Money-$newName.apk"
Copy-Item $apk $asset -Force
Write-Host ("APK: {0} ({1:N1} MB)" -f $asset, ((Get-Item $asset).Length / 1MB))

# --- note di rilascio --------------------------------------------------------------------------
$previous = cmd /c "git describe --tags --abbrev=0 2>nul"
$range = if ($LASTEXITCODE -eq 0 -and $previous) { "$previous..HEAD" } else { "HEAD" }
$log = git log $range --pretty=format:"- %s" --no-merges | Where-Object { $_ -notmatch '^- Release v' }
$notes = "## Novita'`n"
if ($log) { $notes += ($log -join "`n") } else { $notes += "- Miglioramenti e correzioni" }
if ($dirty) { $notes += "`n- Ultime modifiche incluse nella release" }

# --- git e GitHub ------------------------------------------------------------------------------
Step "Commit, tag e push"
Run git @("add", "-A")
Run git @("commit", "-m", "Release $tag")
Run git @("tag", "-a", $tag, "-m", "Money $newName")
Run git @("push", "origin", $branch)
Run git @("push", "origin", $tag)

Step "Pubblico la release su GitHub"
$notesFile = Join-Path $outDir "notes-$newName.md"
[IO.File]::WriteAllText($notesFile, $notes)
Run gh @("release", "create", $tag, $asset, "--title", "Money $newName", "--notes-file", $notesFile)

Write-Host "`nFatto: Money $newName pubblicata." -ForegroundColor Green
gh release view $tag --json url --jq .url
