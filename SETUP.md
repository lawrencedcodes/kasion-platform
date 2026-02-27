# Kasion Platform Setup Guide

This guide provides a detailed, step-by-step walkthrough for setting up the Kasion platform on a new host.

## 1. Prerequisites

Before you begin, please ensure your host machine meets the following requirements:

*   **Operating System:** A modern Linux distribution (e.g., Ubuntu 22.04) or macOS. Windows users should use WSL2.
*   **Docker:** The latest version of Docker must be installed and the Docker daemon must be running. You can verify this by running `docker --version`.
*   **Java Development Kit (JDK):** Kasion requires Java 21 or later. You can verify this by running `java --version`.
*   **Git:** The Git command-line tool must be installed. You can verify this by running `git --version`.
*   **Public IP Address:** Your host should be accessible via a public IP address to access the dashboard and deployed applications.

## 2. Installation

Installation involves cloning the repository and running an automated setup script.

### Step 2.1: Clone the Repository

Open your terminal and clone the official Kasion repository from GitHub:

```bash
git clone https://github.com/lawrencedcodes/kasion-platform.git
cd kasion-platform
```

### Step 2.2: Run the Automated Setup Script

We provide a shell script to automate the initial configuration. This script will:
*   Verify that Docker is running.
*   Create the required `kasion-net` Docker network.
*   Download the necessary Java agents (JMX Exporter and Jolokia) and place them in the correct directories.

To run the script:

```bash
chmod +x setup.sh
./setup.sh
```

If the script completes successfully, you are ready to start the platform.

## 3. Starting the Platform

The entire Kasion stack, including the control plane and all observability tools, is managed via Docker Compose.

### Step 3.1: Navigate to the Control Plane Directory

```bash
cd control-plane
```

### Step 3.2: Launch the Stack

Run the following command to start all services in detached mode:

```bash
docker-compose up -d
```

This will pull the necessary Docker images and start the Kasion control plane, Postgres, Nginx, Prometheus, Grafana, Loki, Alertmanager, and Promtail.

## 4. First-Time Login and Usage

Kasion is secure by default and requires authentication.

### Step 4.1: Retrieve Your Admin Credentials

On the very first run, the Kasion control plane will generate a secure, random password for the default `kasionadmin` user. This password will be printed to the console logs of the Kasion application.

To view the logs and find your password, run:

```bash
docker-compose logs kasion-control-plane
```

Look for a block of text that looks like this:

```
============================================================
Kasion Admin Username: kasionadmin
Kasion Admin Password: <your-random-password-will-be-here>
============================================================
```

**IMPORTANT:** Be sure to copy and save this password in a secure location.

### Step 4.2: Access the Dashboard

Open your web browser and navigate to the public IP address of your host machine:

```
http://<your-host-ip>
```

You will be prompted for a username and password. Use `kasionadmin` and the password you retrieved from the logs.

### Step 4.3: Accessing the Observability Suite

Once logged in, you can also access the integrated tools via the Nginx reverse proxy:

*   **Grafana Dashboard:** `http://<your-host-ip>/grafana/`
*   **Prometheus UI:** `http://<your-host-ip>/prometheus/`
*   **Alertmanager UI:** `http://<your-host-ip>/alertmanager/`

These endpoints are protected by the same authentication.

## 5. Deploying Your First Application

From the Kasion dashboard, click the "New Project" button and provide the URL to a public Git repository containing a Spring Boot (Maven or Gradle) application. Kasion will handle the rest!

Congratulations, your Kasion platform is now fully operational!
