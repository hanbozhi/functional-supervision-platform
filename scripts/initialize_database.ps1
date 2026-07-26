param(
    [string]$PythonCommand = "python",
    [string]$DatabasePath = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "..")
)
$venvDirectory = Join-Path $projectRoot ".venv"
$venvPython = Join-Path $venvDirectory "Scripts\python.exe"
$requirements = Join-Path $PSScriptRoot "requirements.txt"
$importScript = Join-Path $PSScriptRoot "import_rights_to_sqlite.py"
$initConfigPath = Join-Path $PSScriptRoot "database-init.json"
$initConfig = Get-Content -LiteralPath $initConfigPath -Encoding UTF8 -Raw |
    ConvertFrom-Json
$sourceDirectory = Join-Path $projectRoot $initConfig.sourceDirectory
if (-not (Test-Path -LiteralPath $sourceDirectory -PathType Container)) {
    throw "Configured rights source directory does not exist: $sourceDirectory"
}

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Host "Creating Python virtual environment: $venvDirectory"
    & $PythonCommand -m venv $venvDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create the Python virtual environment."
    }
}

Write-Host "Installing database import dependencies..."
& $venvPython -m pip install --disable-pip-version-check -r $requirements
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install the database import dependencies."
}

$importArguments = @(
    $importScript,
    "--src-dir",
    $sourceDirectory,
    "--expected-files",
    [string]$initConfig.expectedSourceFiles,
    "--expected-items",
    [string]$initConfig.expectedRightsItems
)
if ($DatabasePath) {
    $importArguments += @("--db-path", $DatabasePath)
}

Write-Host "Importing rights data..."
& $venvPython @importArguments
if ($LASTEXITCODE -ne 0) {
    throw "Rights data import failed. Existing database data was rolled back."
}

Write-Host ""
Write-Host "Rights data initialization completed."
Write-Host "Start the backend from the backend directory to apply SQL migrations:"
Write-Host "  mvn spring-boot:run"
