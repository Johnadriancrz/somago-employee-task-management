# WorkOS Backend — Java & Spring Boot Setup Guide (VS Code / Windows)

This guide prepares your development environment for the **WorkOS backend**, built with **Java, Spring Boot, Maven, and MySQL**, using **Visual Studio Code** on **Windows**.

It only covers environment setup and project generation. It does **not** implement any API endpoints, database tables, authentication, or business logic — those will follow the approved Frontend API Audit and API Contract as the source of truth.

```text
WorkOS Frontend (Next.js)
        |
        | REST API / JSON
        v
WorkOS Backend (Spring Boot)   <-- this guide sets up the environment for this
        |
        v
MySQL Database
```

---

## Table of Contents

1. [Prerequisites](#a-prerequisites)
2. [Install Java JDK 21](#b-install-java-jdk-21)
3. [Install VS Code Extensions](#c-install-vs-code-extensions)
4. [Generate the Spring Boot Project](#d-generate-the-spring-boot-project)
5. [Open the Project in VS Code](#e-open-the-project-in-vs-code)
6. [Run the Spring Boot Application](#f-run-the-spring-boot-application)
7. [Git Initialization](#g-git-initialization)
8. [Troubleshooting](#h-troubleshooting)
9. [Setup Completion Checklist](#i-setup-completion-checklist)

---

## A. Prerequisites

| Tool | Purpose | Required? |
|---|---|---|
| Visual Studio Code | The code editor used for this project | Required |
| Java JDK 21 (LTS) | Compiles and runs the Spring Boot backend | Required |
| Apache Maven / Maven Wrapper | Builds the project and manages dependencies | Required (wrapper is bundled, no manual install needed) |
| Git | Version control for the backend repository | Required |
| Spring Boot | The application framework used to build the REST API | Required (added via Spring Initializr) |

### What each tool does

- **Visual Studio Code** — A free, lightweight code editor from Microsoft. With the right extensions, it becomes a full Java/Spring IDE.
  Download: https://code.visualstudio.com/download

- **Java JDK 21 (LTS)** — The Java Development Kit. JDK 21 is a **Long-Term Support (LTS)** release, meaning it receives updates and security patches for several years, making it a safe long-term choice for a new backend project.

- **Apache Maven** — A build tool that compiles code, manages dependencies (libraries), and packages the application. You do **not** need to install Maven manually — Spring Initializr generates a project with a **Maven Wrapper** (`mvnw.cmd` on Windows), which downloads the correct Maven version automatically and keeps the build reproducible across machines.
  Reference: https://maven.apache.org/download.cgi

- **Git** — Version control system used to track changes and collaborate.
  Download: https://git-scm.com/downloads

- **Spring Boot** — A framework built on top of Spring that simplifies creating stand-alone, production-ready Java web applications and REST APIs with minimal configuration.
  Reference: https://spring.io/projects/spring-boot

---

## B. Install Java JDK 21

### Recommended distribution

Use **Eclipse Temurin** (from the Eclipse Adoptium project). It is a free, open-source, production-ready OpenJDK build with no licensing restrictions, and is one of the most widely used JDK distributions for Spring Boot development.

Download page: https://adoptium.net/temurin/releases/?version=21

On that page, select:
- **Version:** 21 (LTS)
- **Operating System:** Windows
- **Architecture:** x64 (most Windows PCs) or aarch64 (ARM-based Windows devices)
- **Package Type:** JDK
- **Installer type:** `.msi` (recommended for Windows — handles PATH/JAVA_HOME setup for you)

> Alternative: Oracle also provides JDK 21 builds (https://www.oracle.com/java/technologies/downloads/#java21), but these carry Oracle's commercial licensing terms for production use. Temurin is recommended to avoid licensing ambiguity.

### Step 1 — Run the installer

1. Double-click the downloaded `.msi` file.
2. Follow the installation wizard.
3. **Important:** When prompted, enable these options if shown:
   - "Set or override JAVA_HOME variable"
   - "Add to PATH"

If those options aren't available in your installer, configure them manually in Step 2.

### Step 2 — Configure `JAVA_HOME` (if not set automatically)

1. Press `Win`, search for **"Edit the system environment variables"**, and open it.
2. Click **Environment Variables**.
3. Under **System variables**, click **New**:
   - Variable name: `JAVA_HOME`
   - Variable value: the JDK install path, e.g. `C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot`
4. Click **OK**.

### Step 3 — Add Java to the Windows PATH (if not set automatically)

1. In the same **Environment Variables** window, under **System variables**, select the `Path` variable and click **Edit**.
2. Click **New** and add:
   ```
   %JAVA_HOME%\bin
   ```
3. Click **OK** on all open dialogs to save.

### Step 4 — Restart VS Code (and your terminal)

Environment variable changes are not picked up by already-open programs. **Close and reopen VS Code and any open PowerShell windows** so they load the updated `JAVA_HOME` and `PATH`.

### Step 5 — Verify the installation

Open a **new** PowerShell window and run:

```powershell
java -version
javac -version
```

**Expected output (version numbers may vary slightly):**

```
openjdk version "21.0.x" 2024-xx-xx LTS
OpenJDK Runtime Environment Temurin-21.0.x+x (build 21.0.x+x-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.x+x (build 21.0.x+x-LTS, mixed mode, sharing)
```

```
javac 21.0.x
```

If both commands return version `21.x`, Java is installed correctly.

### Common errors at this stage

| Error | Cause | Fix |
|---|---|---|
| `java is not recognized as an internal or external command` | PATH not updated, or terminal opened before install | Re-check PATH entry, open a **new** terminal window |
| `java -version` shows an older Java version | A previous JDK is earlier in PATH | Reorder PATH entries so the JDK 21 `bin` folder comes first, or uninstall the old JDK |
| `JAVA_HOME` is not found by tools like Maven | `JAVA_HOME` not set or misspelled | Re-check the exact variable name (`JAVA_HOME`, all caps) and that the path points to the JDK folder (not the `bin` subfolder) |

---

## C. Install VS Code Extensions

Two extension packs are required for a full Java + Spring Boot development experience in VS Code.

### 1. Extension Pack for Java (Microsoft) — Required

Marketplace link: https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack

**Install steps:**
1. Open VS Code.
2. Go to the Extensions view (`Ctrl+Shift+X`).
3. Search for **"Extension Pack for Java"**.
4. Click **Install** on the one published by **Microsoft**.

**What it provides:**
- Language Support for Java (syntax highlighting, IntelliSense, refactoring)
- Debugger for Java
- Test Runner for Java (JUnit/TestNG support)
- Maven for Java (run Maven goals from the UI)
- Project Manager for Java (project explorer)
- Visual Studio IntelliCode (AI-assisted completions)

### 2. Spring Boot Extension Pack (VMware) — Required

Marketplace link: https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack

**Install steps:**
1. In the Extensions view, search for **"Spring Boot Extension Pack"**.
2. Click **Install** on the one published by **VMware**.

**What it provides:**
- Spring Boot Tools (auto-completion for `application.properties`/`.yml`, live application info)
- Spring Initializr Java Support (generate new Spring Boot projects directly from VS Code)
- Spring Boot Dashboard (start/stop/debug Spring Boot apps from a sidebar panel)

> Optional: The Spring Boot Dashboard lets you start the app later by clicking a "Run" icon instead of using the terminal command in [Section F](#f-run-the-spring-boot-application). Either method works.

---

## D. Generate the Spring Boot Project

Use **Spring Initializr** to generate the base project: https://start.spring.io/

### Project settings

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot version | Latest stable version compatible with Java 21 (see note below) |
| Group | `com.workos` |
| Artifact | `workos-backend` |
| Name | `workos-backend` |
| Package name | `com.workos.backend` |
| Packaging | Jar |
| Java | 21 |

> **On the Spring Boot version dropdown:** Select the top-most option **not** marked `SNAPSHOT` or `(M)` (milestone) — this is the latest stable release. As of this writing, that is the **Spring Boot 4.1.x** line, which supports Java 17 through Java 26 and is fully compatible with Java 21. Do not select a `SNAPSHOT` or pre-release build.

### Dependencies to add

Click **Add Dependencies** and add each of the following:

| Dependency | Purpose |
|---|---|
| **Spring Web** | Adds the embedded web server (Tomcat) and everything needed to build REST APIs (`@RestController`, JSON handling, etc.) |
| **Spring Data JPA** | Provides ORM support so Java objects can map to MySQL database tables, without writing raw SQL for basic operations |
| **MySQL Driver** | The JDBC driver that allows the application to connect to a MySQL database |
| **Spring Security** | Adds authentication/authorization infrastructure. It is included now so the dependency is in place, but it is **not configured** in this guide — see the note in [Section F](#f-run-the-spring-boot-application) |
| **Validation** | Enables `@Valid`/`@NotNull`-style annotations for validating incoming request data |
| **Lombok** *(optional)* | Reduces boilerplate code (getters/setters/constructors) via annotations. Optional, but commonly used in Spring Boot projects — include it if you want less boilerplate later |

### Generate and download

1. Click **Generate** at the bottom of the page.
2. A `.zip` file (e.g. `workos-backend.zip`) will download.

---

## E. Open the Project in VS Code

### Step 1 — Extract the ZIP file

Right-click the downloaded `workos-backend.zip` → **Extract All...** → choose a destination folder (for example, alongside your existing WorkOS repositories, but as a **separate** project folder — the backend and frontend remain separate repositories).

### Step 2 — Open the folder in VS Code

1. Open VS Code.
2. **File → Open Folder...**
3. Select the extracted `workos-backend` folder.

### Step 3 — Let Maven dependencies download

When the folder opens, the Java extensions will detect the `pom.xml` file and automatically start downloading dependencies in the background. Watch the bottom status bar for progress (e.g. "Java: Loading Projects..." or a spinning icon). This can take a few minutes on first open, depending on your internet connection.

### Step 4 — Identify the important files

| File/Folder | Purpose |
|---|---|
| `pom.xml` | Maven configuration file — lists dependencies, Java version, build plugins |
| `src/main/java/com/workos/backend/WorkosBackendApplication.java` | Main entry point of the Spring Boot application |
| `src/main/resources/application.properties` | Application configuration (server port, database connection, etc.) |
| `src/test/java/...` | Test source folder |
| `mvnw`, `mvnw.cmd` | Maven Wrapper scripts (`mvnw.cmd` is used on Windows) |
| `target/` | Build output folder (created after building/running — not committed to Git) |

### Sample directory tree

```text
workos-backend/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── workos/
│   │   │           └── backend/
│   │   │               └── WorkosBackendApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       └── templates/
│   └── test/
│       └── java/
│           └── com/
│               └── workos/
│                   └── backend/
│                       └── WorkosBackendApplicationTests.java
├── mvnw
├── mvnw.cmd
├── pom.xml
└── .gitignore
```

---

## F. Run the Spring Boot Application

### Step 1 — Open a terminal in the project folder

In VS Code: **Terminal → New Terminal**. Confirm it opens in the `workos-backend` project root (where `mvnw.cmd` is located).

### Step 2 — Run using the Maven Wrapper

```powershell
.\mvnw.cmd spring-boot:run
```

> **Required, not optional:** Always use the Maven Wrapper (`mvnw.cmd`) rather than a globally installed `mvn`, so the whole team builds with the exact same Maven version regardless of what (if anything) is installed on their machine.

### Step 3 — Identify a successful startup

A successful startup shows Spring Boot's ASCII banner followed by log lines ending with something similar to:

```
Tomcat started on port(s): 8080 (http) with context path ''
Started WorkosBackendApplication in x.xxx seconds (process running for x.xxx)
```

The default port is **8080**. The application is now running at `http://localhost:8080`.

### Step 4 — Expected browser behavior

> **Important note:** Because **Spring Security** is on the classpath and no API endpoints or security rules have been configured yet, visiting `http://localhost:8080` in a browser may show:
> - A default Spring Security **login page**, or
> - An HTTP **401 Unauthorized** response, or
> - A generic **Whitelabel Error Page**
>
> This is **expected** at this stage — it confirms the application started correctly. It does **not** indicate a bug, and no endpoints, controllers, or security configuration should be added as part of this setup guide. Actual API endpoints will be implemented later based on the approved API Contract.

### Step 5 — Stop the server

In the terminal running the application, press:

```
Ctrl+C
```

Confirm if prompted (e.g. `Terminate batch job (Y/N)?` — type `Y` and press Enter).

### Troubleshooting startup errors

See [Section H](#h-troubleshooting) below.

---

## G. Git Initialization

> **Only run this section if the generated project is not already a Git repository.** Spring Initializr does **not** initialize Git automatically, so this will normally apply — but check first.

### Step 1 — Check Git status

From the `workos-backend` project root:

```powershell
git status
```

- If you see `fatal: not a git repository...`, proceed to Step 2.
- If you see a valid status output instead (e.g. "On branch main"), **the repository is already initialized** — do not run `git init` again. Skip to committing any new/changed files if needed.

### Step 2 — Initialize the repository

```powershell
git init
```

### Step 3 — Stage files

```powershell
git add .
```

> The generated `.gitignore` already excludes the `target/` build folder and common IDE files, so this is safe to run as-is.

### Step 4 — Create the initial commit

```powershell
git commit -m "Initial commit: WorkOS backend project scaffold (Spring Boot)"
```

---

## H. Troubleshooting

| Issue | Solution |
|---|---|
| **`java is not recognized`** | The JDK `bin` folder isn't on PATH, or you're using an old terminal session. Re-check the `Path` variable includes `%JAVA_HOME%\bin`, then open a brand-new PowerShell window. |
| **`JAVA_HOME` is missing or incorrect** | Open System Environment Variables and confirm `JAVA_HOME` points directly to the JDK folder (e.g. `C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot`), not to a `bin` subfolder or a JRE. Restart VS Code/terminal after changing it. |
| **Java version mismatch** (e.g. `java -version` shows Java 8 or 17 instead of 21) | Multiple JDKs are installed and an older one appears earlier in PATH. Move the JDK 21 entry above older ones in the `Path` variable, or uninstall unused JDKs. |
| **Maven Wrapper errors** (e.g. `mvnw.cmd` fails to download Maven, or `'.\mvnw.cmd' is not recognized`) | Ensure you're running the command from inside the `workos-backend` project root where `mvnw.cmd` exists. Check your internet connection (the wrapper downloads Maven on first run). If corporate network/proxy is blocking downloads, configure a proxy in `~/.m2/settings.xml` or connect via a different network. |
| **VS Code not detecting Java / red squiggly errors everywhere** | Open the Command Palette (`Ctrl+Shift+P`) → run **"Java: Clean Java Language Server Workspace"** → reload when prompted. Confirm the Java extensions are installed and `JAVA_HOME` is correctly set. |
| **Port 8080 already in use** | Another process (possibly a previous run that didn't stop cleanly) is using port 8080. Either stop that process (`Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess` then `Stop-Process`), or temporarily run on a different port: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`. |
| **Spring Boot application fails to start** | Read the **first** error in the stack trace, not the last — Spring Boot often prints a summarized "Description"/"Action" block near the top explaining the real cause (e.g. missing MySQL connection when a database URL isn't configured yet, port conflicts, or a bad `application.properties` value). Since no database is configured in this setup guide, database connection errors are expected only if `spring.datasource.*` properties were manually added — leave `application.properties` empty/default for now. |

---

## I. Setup Completion Checklist

- [ ] JDK 21 is installed (`java -version` reports `21.x`)
- [ ] `java -version` and `javac -version` both work in a new terminal
- [ ] `JAVA_HOME` is set and points to the JDK 21 install folder
- [ ] VS Code is installed
- [ ] **Extension Pack for Java** (Microsoft) is installed
- [ ] **Spring Boot Extension Pack** (VMware) is installed
- [ ] Spring Boot project generated via Spring Initializr with the settings in [Section D](#d-generate-the-spring-boot-project)
- [ ] Project extracted and opened in VS Code as its own folder
- [ ] Maven dependencies resolved (no errors in the Java/Maven output panels)
- [ ] `.\mvnw.cmd spring-boot:run` starts the application successfully on port 8080
- [ ] Browser shows a login page / 401 / Whitelabel error at `http://localhost:8080` (expected — confirms the app is running)
- [ ] Git is initialized (`git status` shows a valid repository)
- [ ] Initial commit created

---

## Scope Reminder

This guide only prepares the environment and generates the base project scaffold. As agreed for this task, the following are **explicitly out of scope** here and will be addressed in later, separate work:

- No API endpoints implemented
- No database tables created
- No authentication configured
- No custom controllers, services, or repositories beyond the Spring Initializr defaults
- No changes to the existing WorkOS frontend

The backend and frontend remain separate projects/repositories, connected only via REST API/JSON once implementation begins.
