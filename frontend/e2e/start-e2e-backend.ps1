$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..")
$backendDir = Join-Path $repoRoot "backend"
$targetDir = Join-Path $backendDir "target"

if (-not (Test-Path -LiteralPath $targetDir)) {
  New-Item -ItemType Directory -Path $targetDir | Out-Null
}

$dbFiles = @(
  (Join-Path $targetDir "jobhub-e2e.db"),
  (Join-Path $targetDir "jobhub-e2e.db-shm"),
  (Join-Path $targetDir "jobhub-e2e.db-wal")
)

foreach ($dbFile in $dbFiles) {
  $fullPath = [System.IO.Path]::GetFullPath($dbFile)
  $targetRoot = [System.IO.Path]::GetFullPath($targetDir)
  if (-not $fullPath.StartsWith($targetRoot)) {
    throw "Refusing to delete outside backend target: $fullPath"
  }
  if (Test-Path -LiteralPath $fullPath) {
    Remove-Item -LiteralPath $fullPath -Force
  }
}

$env:JOBHUB_DB_PATH = "./target/jobhub-e2e.db"
mvn -f (Join-Path $backendDir "pom.xml") spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"
