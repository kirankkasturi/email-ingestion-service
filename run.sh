#!/bin/bash
# =============================================================================
# Email Ingestion Service — Mac Setup & Run Script
# =============================================================================
#
# WHAT THIS DOES (you don't need to know any of this to use it):
# 1. Installs Homebrew (Mac package manager) if not already installed
# 2. Installs Java 17 if not already installed
# 3. Installs Maven (build tool) if not already installed
# 4. Builds the application
# 5. Starts the service on http://localhost:8080
#
# HOW TO RUN:
# Step 1 — Open Terminal
# (Press Cmd+Space, type "Terminal", press Enter)
#
# Step 2 — Navigate to this folder
# Type exactly: cd ~/Downloads/email-ingestion-service
# Press Enter
#
# Step 3 — Allow the script to run
# Type exactly: chmod +x run.sh
# Press Enter
#
# Step 4 — Run it
# Type exactly: ./run.sh
# Press Enter
#
# The service is ready when you see:
# "Started EmailIngestionServiceApplication on port 8080"
#
# To stop it: press Ctrl+C
# =============================================================================

set -e # Stop immediately if any command fails

echo ""
echo "=============================================="
echo " Email Ingestion Service — Starting Setup"
echo "=============================================="
echo ""

# --- Step 1: Install Homebrew if missing ---
if ! command -v brew &>/dev/null; then
 echo "[1/4] Installing Homebrew (this may take a few minutes)..."
 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

 # Add Homebrew to PATH for Apple Silicon Macs
 if [[ -f "/opt/homebrew/bin/brew" ]]; then
 eval "$(/opt/homebrew/bin/brew shellenv)"
 fi
 echo " Homebrew installed."
else
 echo "[1/4] Homebrew already installed. Skipping."
fi

# --- Step 2: Install Java 17 if missing ---
if java -version 2>&1 | grep -q "version \"17"; then
 echo "[2/4] Java 17 already installed. Skipping."
else
 echo "[2/4] Installing Java 17..."
 brew install openjdk@17

 # Link so 'java' command works system-wide
 JAVA_HOME_PATH="$(brew --prefix openjdk@17)"
 export PATH="${JAVA_HOME_PATH}/bin:$PATH"
 export JAVA_HOME="${JAVA_HOME_PATH}"
 echo " Java 17 installed."
fi

# Ensure JAVA_HOME is set even if Java was already installed via brew
if [[ -z "$JAVA_HOME" ]] && brew list openjdk@17 &>/dev/null; then
 export JAVA_HOME="$(brew --prefix openjdk@17)"
 export PATH="$JAVA_HOME/bin:$PATH"
fi

# --- Step 3: Install Maven if missing ---
if command -v mvn &>/dev/null; then
 echo "[3/4] Maven already installed. Skipping."
else
 echo "[3/4] Installing Maven..."
 brew install maven
 echo " Maven installed."
fi

# --- Step 4: Build the application ---
echo "[4/4] Building the application (downloading dependencies + compiling)..."
echo " This may take 1-2 minutes the first time..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mvn clean package -q

echo ""
echo "=============================================="
echo " Build successful! Starting the service..."
echo "=============================================="
echo ""
echo " The service will be available at:"
echo " http://localhost:8080"
echo ""
echo " To stop the service, press Ctrl+C"
echo "=============================================="
echo ""

# --- Step 5: Run ---
mvn spring-boot:run