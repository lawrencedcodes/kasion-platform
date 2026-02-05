# 🏛️ PROJECT CONTEXT: KASION PLATFORM (The Java PaaS)

## 1. THE MISSION
We are building **Kasion**, a self-hosted Platform-as-a-Service (PaaS) specifically optimized for Spring Boot applications.
**The Goal:** Provide the "Heroku Experience" (Git-push-to-deploy) on cheap ($5/mo) VPS infrastructure (Hetzner/DigitalOcean).

**Core Philosophy:**
* **"Just Enough":** No Kubernetes complexity. No heavy orchestration. Just Docker and Java.
* **Java Native:** The control plane itself is a Spring Boot app that deeply understands the apps it manages (Actuator, JMX, JVM tuning).
* **Zero Config:** Users paste a URL, we detect the build strategy (`mvnw` vs `mvn`) automatically.

---

## 2. SYSTEM ARCHITECTURE

Kasion operates as a "Meta-Application" (The Control Plane) that manages sibling containers.

### The Components
1.  **The Control Plane (Host):**
    * Spring Boot Application (Java 21).
    * Connects to the Host Docker Socket (`/var/run/docker.sock`) to orchestrate containers.
    * Manages the specialized bridge network (`kasion-net`).

2.  **The Build Engine (Async Service):**
    * **Step 1:** Clones public GitHub Repos via system Git.
    * **Step 2:** Detects `mvnw` (Wrapper) or falls back to system Maven.
    * **Step 3:** Builds the artifacts (`.jar`).
    * **Step 4:** Generates a dynamic `Dockerfile` (Jre-slim based).
    * **Step 5:** Builds and Tags the Docker Image.

3.  **The Runtime (User Apps):**
    * Deployed as isolated Docker containers.
    * Attached to `kasion-net`.
    * Accessible via mapped ports (currently `8081`, `8082`, etc.).

---

## 3. CURRENT STATUS (v0.2 -> v0.3)

**✅ Implemented:**
* Git-to-URL cloning (handling public repos).
* Universal Maven Build Strategy (Wrapper detection).
* Basic Docker Orchestration (Start/Stop/Logs).
* Live Dashboard (Deployment status).

**🚧 Active Roadmap (The "Next Steps" for AI):**
* **Managed Databases (v0.3):** We need to auto-provision Postgres containers and inject their credentials (`SPRING_DATASOURCE_URL`) into the user app automatically.
* **Zero-Downtime Deployment (v0.4):** Implementing Blue/Green switchovers (spinning up the new container before killing the old one).
* **Log Streaming:** Improving the real-time WebSocket pipe from Docker logs to the Frontend UI.

---

## 4. DEVELOPMENT GUIDELINES for AI

**Technological Constraints:**
1.  **Java 21:** Use modern features (Records, Virtual Threads for build tasks).
2.  **Spring Boot:** Use standard dependency injection and Events for decoupling the Build Service from the Controller.
3.  **Docker Interaction:** We interact with Docker via command-line wrappers (`ProcessBuilder`) or the official Java Docker Client. *Check `pom.xml` to confirm which one we use.*

**Critical Rules:**
* **Network Isolation:** All user apps MUST launch on `kasion-net`. Never launch on the default bridge.
* **Resource Limits:** When generating Docker commands, always consider adding memory limits (`-m 512m`) to prevent OOM kills on cheap VPSs.
* **Port Management:** The Control Plane must track used ports to avoid collisions.

---

## 5. CLI INTERACTION CHEATSHEET
* **Start Control Plane:** `./mvnw spring-boot:run`
* **Infrastructure Init:** `docker network create kasion-net`
* **Dashboard:** `http://localhost:8080`
