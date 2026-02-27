#!/bin/bash
# Kasion Platform Setup Script

# --- Configuration ---
JMX_EXPORTER_VERSION="1.0.1"
JOLOKIA_VERSION="1.7.2"
JMX_EXPORTER_URL="https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar"
JOLOKIA_URL="https://repo1.maven.org/maven2/org/jolokia/jolokia-jvm/${JOLOKIA_VERSION}/jolokia-jvm-${JOLOKIA_VERSION}-agent.jar"

# --- Colors ---
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# --- Functions ---

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# --- Main Script ---

echo -e "${GREEN}🚀 Starting Kasion Platform Setup...${NC}"

# 1. Prerequisite Checks
echo -e "\n${YELLOW}Step 1: Checking prerequisites...${NC}"

if ! command_exists docker; then
    echo -e "${RED}Error: Docker is not installed. Please install Docker and try again.${NC}"
    exit 1
fi
echo -e "  - Docker found."

if ! command_exists git; then
    echo -e "${RED}Error: Git is not installed. Please install Git and try again.${NC}"
    exit 1
fi
echo -e "  - Git found."

echo -e "${GREEN}  ✔ Prerequisites met.${NC}"

# 2. Create Docker Network
echo -e "\n${YELLOW}Step 2: Setting up Docker network...${NC}"
if [ -z "$(docker network ls --filter name=^kasion-net$ --format=\"{{.Name}}\")" ]; then
    docker network create kasion-net
    echo -e "  - Docker network 'kasion-net' created." 
else
    echo -e "  - Docker network 'kasion-net' already exists. Skipping."
fi
echo -e "${GREEN}  ✔ Docker network configured.${NC}"

# 3. Create Agent Directories
echo -e "\n${YELLOW}Step 3: Creating directories for Java agents...${NC}"
mkdir -p control-plane/jmx_exporter
mkdir -p control-plane/jolokia
echo -e "  - Directories 'control-plane/jmx_exporter' and 'control-plane/jolokia' ensured."
echo -e "${GREEN}  ✔ Agent directories created.${NC}"

# 4. Download Java Agents
echo -e "\n${YELLOW}Step 4: Downloading Java agents...${NC}"

# JMX Exporter
if [ ! -f "control-plane/jmx_exporter/jmx_prometheus_javaagent.jar" ]; then
    echo "  - Downloading JMX Exporter agent..."
    curl -L -o control-plane/jmx_exporter/jmx_prometheus_javaagent.jar "$JMX_EXPORTER_URL"
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Failed to download JMX Exporter agent. Please check your internet connection.${NC}"
        exit 1
    fi
else
    echo "  - JMX Exporter agent already exists. Skipping."
fi

# Jolokia
if [ ! -f "control-plane/jolokia/jolokia-jvm-agent.jar" ]; then
    echo "  - Downloading Jolokia agent..."
    curl -L -o control-plane/jolokia/jolokia-jvm-agent.jar "$JOLOKIA_URL"
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Failed to download Jolokia agent. Please check your internet connection.${NC}"
        exit 1
    fi
else
    echo "  - Jolokia agent already exists. Skipping."
fi
echo -e "${GREEN}  ✔ Java agents are ready.${NC}"

echo -e "\n${GREEN}🎉 Kasion setup complete!${NC}"
echo -e "You can now start the platform by running 'docker-compose up -d' in the 'control-plane' directory."
