# =============================================================================
# Email Ingestion Service — Windows Setup & Run Script
# =============================================================================
#
# WHAT THIS DOES (you don't need to know any of this to use it):
# 1. Installs Java 17 if not already installed (via winget)
# 2. Installs Maven (build tool) if not already installed (via winget)
# 3. Builds the application
# 4. Starts the service on http://localhost:8080
#
# HOW TO RUN:
# Step 1 — Open PowerShell as Administrator
# Press the Windows key, type "PowerShell"
# Right-click "Windows PowerShell" -> "Run as Administrator"
# Click "Yes" when prompted
#
# Step 2 — Allow scripts to run (one-time setup)
# Type exactly: Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
# Press Enter, then type Y and press Enter
#
# Step 3 — Navigate to this folder
# Type exactly: cd "$env:USERPROFILE\Downloads\email-ingestion-service"
# Press Enter
#
# Step 4 — Run it
# Type exactly: .\run.ps1
# Press Enter
#
# The service is ready when you see:
# "Started EmailIngestionServiceApplication on port 8080"
#
# To stop it: press Ctrl+C
# =============================================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=============================================="
Write-Host " Email Ingestion Service — Starting Setup"
Write-Host "=============================================="
Write-Host ""

# Helper: reload PATH within the same session after installs
function Refresh-Path {
 $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
 [System.Environment]::GetEnvironmentVariable("Path", "User")
}

# --- Step 1: Install Java 17 if missing ---
$javaOk = $false
try {
 $javaVersion = & java -version 2>&1
 if ($javaVersion -match "17") { $javaOk = $true }
} catch {}

if ($javaOk) {
 Write-Host "[1/3] Java 17 already installed. Skipping."
} else {
 Write-Host "[1/3] Installing Java 17 via winget..."
 Write-Host " (You may see a UAC prompt — click Yes to allow)"

 # Try winget first (available on Windows 10 1709+ and Windows 11)
 $wingetAvailable = $null -ne (Get-Command winget -ErrorAction SilentlyContinue)

 if ($wingetAvailable) {
 winget install --id Microsoft.OpenJDK.17 --accept-source-agreements --accept-package-agreements --silent
 Refresh-Path
 } else {
 # Fallback: download Eclipse Temurin 17 installer directly
 Write-Host " winget not found. Downloading Java 17 installer directly..."
 $javaInstaller = "$env:TEMP\java17-installer.msi"
 $javaUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.11_9.msi"
 Invoke-WebRequest -Uri $javaUrl -OutFile $javaInstaller -UseBasicParsing
 Start-Process msiexec.exe -ArgumentList "/i `"$javaInstaller`" /quiet ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome" -Wait
 Refresh-Path
 Remove-Item $javaInstaller -ErrorAction SilentlyContinue
 }
 Write-Host " Java 17 installed."
}

# --- Step 2: Install Maven if missing ---
$mvnOk = $null -ne (Get-Command mvn -ErrorAction SilentlyContinue)

if ($mvnOk) {
 Write-Host "[2/3] Maven already installed. Skipping."
} else {
 Write-Host "[2/3] Installing Maven..."

 $wingetAvailable = $null -ne (Get-Command winget -ErrorAction SilentlyContinue)

 if ($wingetAvailable) {
 winget install --id Apache.Maven --accept-source-agreements --accept-package-agreements --silent
 Refresh-Path
 } else {
 # Fallback: download Maven zip and extract to C:\maven
 Write-Host " winget not found. Downloading Maven directly..."
 $mavenVersion = "3.9.6"
 $mavenZip = "$env:TEMP\maven.zip"
 $mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
 $mavenDir = "C:\maven"

 Invoke-WebRequest -Uri $mavenUrl -OutFile $mavenZip -UseBasicParsing
 Expand-Archive -Path $mavenZip -DestinationPath $mavenDir -Force
 Remove-Item $mavenZip -ErrorAction SilentlyContinue

 # Add to system PATH permanently
 $mavenBin = "$mavenDir\apache-maven-$mavenVersion\bin"
 $currentPath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
 if ($currentPath -notlike "*$mavenBin*") {
 [System.Environment]::SetEnvironmentVariable("Path", "$currentPath;$mavenBin", "Machine")
 }
 Refresh-Path
 }
 Write-Host " Maven installed."
}

# Final PATH refresh before build
Refresh-Path

# --- Step 3: Build the application ---
Write-Host "[3/3] Building the application (downloading dependencies + compiling)..."
Write-Host " This may take 1-2 minutes the first time..."
Write-Host ""

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

& mvn clean package -q
if ($LASTEXITCODE -ne 0) {
 Write-Host ""
 Write-Host "ERROR: Build failed. Check the output above for details." -ForegroundColor Red
 exit 1
}

Write-Host ""
Write-Host "=============================================="
Write-Host " Build successful! Starting the service..."
Write-Host "=============================================="
Write-Host ""
Write-Host " The service will be available at:"
Write-Host " http://localhost:8080"
Write-Host ""
Write-Host " To stop the service, press Ctrl+C"
Write-Host "=============================================="
Write-Host ""

# --- Step 4: Run ---
& mvn spring-boot:run