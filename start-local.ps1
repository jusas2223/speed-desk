[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$speedDeskRoot = $PSScriptRoot
$backendDirectory = Join-Path $speedDeskRoot 'backend'
$mavenWrapper = Join-Path $backendDirectory 'mvnw.cmd'
$localDatabasePath = Join-Path $speedDeskRoot '.speeddesk-local\speeddesk'

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven Wrapper nao encontrado em: $mavenWrapper"
}

$configuredJava = $env:JAVA_HOME
if (-not $configuredJava -or -not (Test-Path -LiteralPath (Join-Path $configuredJava 'bin\java.exe') -PathType Leaf)) {
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    $jdk26 = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -match '26' -and
            (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') -PathType Leaf)
        } |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if (-not $jdk26) {
        throw 'JDK 26 nao encontrado. Configure JAVA_HOME antes de iniciar o Speed Desk.'
    }

    $env:JAVA_HOME = $jdk26.FullName
}

$env:SPRING_PROFILES_ACTIVE = 'localdev'
$env:SPEEDDESK_LOCAL_DB_PATH = $localDatabasePath.Replace('\', '/')
$env:SPEEDDESK_CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5500,http://localhost:5500'

if (-not $env:SPEEDDESK_JWT_SECRET) {
    $env:SPEEDDESK_JWT_SECRET = 'localdev-only-secret-with-at-least-32-bytes'
}

Write-Host "Speed Desk localdev"
Write-Host "Java: $env:JAVA_HOME"
Write-Host "Banco H2: $env:SPEEDDESK_LOCAL_DB_PATH"

Push-Location $backendDirectory
try {
    & $mavenWrapper spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "O backend terminou com codigo $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
