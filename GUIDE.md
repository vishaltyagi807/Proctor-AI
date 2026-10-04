# ProctorAI Setup and Build Guide

## Choose your path

| I want to… | Fastest route | Time | Needs |
|---|---|---|---|
| ⚡ **Run ProctorAI on a server** | [Deploy with published Docker images](#3-deploy-with-published-docker-images) | About 5 minutes | Docker only |
| 🛠️ **Work on the code** | [Development](#4-development) | About 15 minutes the first time | Docker only |
| 🏗️ **Run my own changes in production** | [Production from source](#5-production-from-source) | About 20 minutes | Docker only |
| 📱 **Build the desktop or mobile apps** | [Desktop and mobile apps](#6-desktop-and-mobile-apps) | About 30 minutes the first time | Node.js, Rust and the platform tools |

Starting on a machine with nothing installed? Install the tools first in [section 2](#2-install-everything-from-scratch). It tells you exactly which parts each path needs.

## Contents

1. [What you are setting up](#1-what-you-are-setting-up)
2. [Install everything from scratch](#2-install-everything-from-scratch)
3. [Deploy with published Docker images](#3-deploy-with-published-docker-images)
4. [Development](#4-development)
5. [Production from source](#5-production-from-source)
6. [Desktop and mobile apps](#6-desktop-and-mobile-apps)
7. [Troubleshooting](#7-troubleshooting)
8. [Command reference](#8-command-reference)

All commands run from the repository root unless a step says otherwise.

---

## 1. What you are setting up

| Part | What it is | Where |
|---|---|---|
| Backend | 12 Spring Boot services behind an API gateway, with PostgreSQL, Redis and MinIO | `backend/` |
| Web app | React and Vite, served by Nginx in production | `frontend/` |
| Desktop and mobile apps | The same web app packaged with Tauri 2 for Windows, macOS, Linux, Android and iOS | `frontend/src-tauri/` |
| Edge proxy (production) | Caddy with automatic HTTPS certificates | generated into `edge/` next to the setup script |
| Published images | Every service, prebuilt, in `vishaltyagi807/proctor-ai` on Docker Hub | `docker/` |

Four scripts drive everything:

| Script | Purpose |
|---|---|
| `docker/setup.sh` | Runs the production stack from the published images. Nothing is built. |
| `./setup.sh` | Builds from source, then starts and manages the development and production stacks |
| `./native-doctor.sh` | Checks that a machine can build the desktop and mobile apps |
| `npm run …` in `frontend/` | Builds the web, desktop and mobile apps |

---

## 2. Install everything from scratch

Start here on a machine that has nothing installed. Follow the steps for your operating system. You only need the parts for what you plan to do:

| Goal | Steps |
|---|---|
| Deploy with published Docker images | [2.1](#21-get-the-code), [2.2](#22-install-docker) and [2.3](#23-prepare-a-production-server) |
| Run ProctorAI for development | [2.1](#21-get-the-code) and [2.2](#22-install-docker) |
| Run ProctorAI in production from source | [2.1](#21-get-the-code), [2.2](#22-install-docker) and [2.3](#23-prepare-a-production-server) |
| Build the desktop app | [2.1](#21-get-the-code), [2.4](#24-install-nodejs-and-rust) and [2.5](#25-install-the-desktop-build-tools) |
| Build the Android app | [2.1](#21-get-the-code), [2.4](#24-install-nodejs-and-rust) and [2.6](#26-install-the-android-toolchain) |
| Build the iOS app (Mac only) | [2.1](#21-get-the-code), [2.4](#24-install-nodejs-and-rust) and [2.7](#27-install-the-ios-toolchain) |

Minimum hardware: 8 GB of RAM (16 GB recommended) and 20 GB of free disk space, or 40 GB if you also build the Android app.

> **Windows:** run every `./…` command in this guide in **Git Bash**, which comes with Git for Windows. PowerShell commands are marked as such.

### 2.1 Get the code

**Linux (Ubuntu or Debian):**

```bash
sudo apt update
sudo apt install -y git curl
```

**macOS:**

```bash
xcode-select --install
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

This installs Git, the Apple command line tools and Homebrew. Accept the dialog that opens and wait for it to finish.

**Windows (PowerShell):**

```powershell
winget install --id Git.Git -e
```

Close and reopen the terminal afterwards. Then, on every system:

```bash
git clone https://github.com/vishaltyagi807/Proctor-AI.git
cd Proctor-AI
```


Cloning with Git keeps line endings and file permissions correct. If you download a ZIP instead and `./setup.sh` reports `Permission denied`, run the scripts as `sh setup.sh` and `sh native-doctor.sh`.

### 2.2 Install Docker

**Linux:**

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

Log out and back in (or run `newgrp docker`) so your user can run Docker without `sudo`. Check it with `docker run --rm hello-world`.

**macOS:** install [Docker Desktop](https://www.docker.com/products/docker-desktop/), or with Homebrew:

```bash
brew install --cask docker
```

Homebrew is already installed from step 2.1. Open Docker from Applications once and wait until it says it is running. In **Settings > Resources**, give it at least 8 GB of memory.

**Windows (PowerShell, as administrator):**

```powershell
wsl --install
winget install --id Docker.DockerDesktop -e
```

Restart the computer, open Docker Desktop, and wait until it says it is running.

That is everything needed to run ProctorAI. Continue with [Development](#4-development), or with 2.3 for a server.

### 2.3 Prepare a production server

On a fresh Ubuntu 22.04 or 24.04 server, do steps 2.1 and 2.2, then open the firewall:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw allow 9200/tcp
sudo ufw enable
```

Port 9200 serves uploaded files only until your domains are set up. Close it afterwards with `sudo ufw delete allow 9200/tcp`. Also open the same ports in your cloud provider's firewall or security group, if it has one.

Continue with [Deploy with published Docker images](#3-deploy-with-published-docker-images) for the fastest setup, or [Production from source](#5-production-from-source) to run your own build.

### 2.4 Install Node.js and Rust

Every desktop and mobile build needs Node.js (20.19+ or 22.12+) and Rust (1.90+).

**Linux and macOS:**

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
. "$HOME/.nvm/nvm.sh"
nvm install --lts

curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
. "$HOME/.cargo/env"
```

**Windows (PowerShell):**

```powershell
winget install --id OpenJS.NodeJS.LTS -e
winget install --id Rustlang.Rustup -e
```

Close and reopen the terminal. Then, on every system, install the frontend packages:

```bash
cd frontend
npm ci
cd ..
```

### 2.5 Install the desktop build tools

Each operating system builds its own installers. Use Windows for `.msi` and `.exe`, macOS for `.app` and `.dmg`, and Linux for `.deb`, `.rpm` and `.AppImage`.

**Linux (Ubuntu or Debian):**

```bash
sudo apt install -y libwebkit2gtk-4.1-dev build-essential curl wget file libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev
```

For Fedora, Arch or openSUSE, `./native-doctor.sh desktop` prints the matching command.

**macOS:** the command line tools from step 2.1 are enough. For a single build that runs on both Intel and Apple Silicon Macs, also run:

```bash
rustup target add aarch64-apple-darwin x86_64-apple-darwin
```

**Windows (PowerShell):**

```powershell
winget install --id Microsoft.VisualStudio.2022.BuildTools -e --override "--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
winget install --id Microsoft.EdgeWebView2Runtime -e
rustup default stable-msvc
```

WebView2 is already part of Windows 10 (version 1803 and later) and Windows 11, so its install may report that it is already installed.

Check the result:

```bash
./native-doctor.sh desktop
```

### 2.6 Install the Android toolchain

Works on Linux, macOS and Windows.

**1. Install Android Studio.**

| OS | Command |
|---|---|
| Linux | `sudo snap install android-studio --classic`, or download it from https://developer.android.com/studio |
| macOS | `brew install --cask android-studio` |
| Windows (PowerShell) | `winget install --id Google.AndroidStudio -e` |

Open Android Studio once and finish the setup wizard. It downloads the Android SDK.

**2. Install the SDK parts.** In Android Studio, open **Settings > Languages & Frameworks > Android SDK**:

- On **SDK Platforms**, check the newest Android version.
- On **SDK Tools**, check **Android SDK Build-Tools**, **NDK (Side by side)**, **Android SDK Command-line Tools** and **Android SDK Platform-Tools**.

Click **Apply** and accept the licenses.

**3. Set the environment variables.**

Linux: install a JDK, then add the variables to `~/.bashrc`:

```bash
sudo apt install -y openjdk-17-jdk
```

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export ANDROID_HOME="$HOME/Android/Sdk"
export NDK_HOME="$ANDROID_HOME/ndk/$(ls -1 "$ANDROID_HOME/ndk" | sort -V | tail -n 1)"
```

macOS, in `~/.zshrc`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export NDK_HOME="$ANDROID_HOME/ndk/$(ls -1 "$ANDROID_HOME/ndk" | sort -V | tail -n 1)"
```

Windows (PowerShell). Replace `<version>` with the folder name inside `%LOCALAPPDATA%\Android\Sdk\ndk`:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("NDK_HOME", "$env:LOCALAPPDATA\Android\Sdk\ndk\<version>", "User")
```

Open a new terminal so the variables take effect.

**4. Add the Rust Android targets:**

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

**5. Check the result:**

```bash
./native-doctor.sh android
```

### 2.7 Install the iOS toolchain

iOS apps can only be built on a Mac.

1. Install **Xcode** from the App Store and open it once to finish its setup.
2. Run:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app
   sudo xcodebuild -license accept
   brew install cocoapods
   rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios
   ```

3. In Xcode, open **Settings > Components** and install the iOS simulator.
4. To build for real devices or the App Store, sign in with your Apple ID under **Xcode > Settings > Accounts**, then note your Team ID for section 6.5.
5. Check the result:

   ```bash
   ./native-doctor.sh ios
   ```

---

## 3. Deploy with published Docker images

This is the fastest way to run ProctorAI. Every service is already built and published in [`vishaltyagi807/proctor-ai`](https://hub.docker.com/r/vishaltyagi807/proctor-ai) on Docker Hub, so the server only downloads and starts them. There's no Java, Node.js or Gradle build, and no model download. The face models are already inside the image.

| | Published images (`docker/setup.sh`) | From source (`./setup.sh prod`) |
|---|---|---|
| First start | About 5 minutes, mostly download time | About 20 minutes, building every service |
| Needs on the server | Docker and the `docker/` folder | Docker and the full repository |
| Runs | The released version | Your local code |
| Best for | Production servers, demos, evaluation | Running your own changes in production |

### 3.1 Get the deployment folder

You only need the `docker/` folder on the server. Do one of the following:

```bash
git clone --depth 1 https://github.com/vishaltyagi807/Proctor-AI.git
cd Proctor-AI/docker
```

or copy the folder from another machine:

```bash
scp -r docker <user>@<server-ip>:~/proctorai
ssh <user>@<server-ip>
cd ~/proctorai
```

### 3.2 Start ProctorAI

```bash
./setup.sh
```

Setup asks for the web app, file storage and service registry domains and an email for TLS certificates. Leave the domains empty to start on the server IP address. Then it does the following:

1. Creates `.env` with new random secrets
2. Detects the public IP address, checks DNS and configures automatic HTTPS
3. Pulls the images and starts every container, then waits until all of them are healthy
4. Replaces the default administrator password with a random one and **shows it once**. It is also saved as `ADMIN_PASSWORD` in `.env`.
5. Prints the URLs and the DNS records that are still missing

Sign in as `admin@college.com` with the password it shows, then change the password in your profile.

If the Docker Hub repository is private, run `docker login` first.

### 3.3 Domains and HTTPS

The domains, DNS records, firewall ports and the step from IP address to HTTPS work exactly as in [5.2 DNS records](#52-dns-records) and [5.3 Before and after DNS](#53-before-and-after-dns). In short:

```bash
./setup.sh dns     # shows each DNS record and whether it is in place
./setup.sh         # run again once the records are in place, to switch to HTTPS
```

### 3.4 Everyday commands

Run these inside the deployment folder:

| Command | Does |
|---|---|
| `./setup.sh update` | Pull the images again and restart, keeping all data |
| `./setup.sh update 1.1.0` | Switch to another published version |
| `./setup.sh status` | Show every container and its health |
| `./setup.sh logs` or `./setup.sh logs edge` | Follow logs |
| `./setup.sh stop` / `./setup.sh down` | Stop or remove the containers, keeping all data |
| `./setup.sh purge` | Delete the containers and all data, including certificates (asks to confirm) |

The settings live in `.env` in the deployment folder and work as described in [5.5 Configuration](#55-configuration). For backups, use the commands in [5.8 Backups](#58-backups) with `PROD="docker compose -f docker-compose.yaml --env-file .env"`.

> Use one method per server. The published-image deployment and `./setup.sh prod` share the same Docker volumes, but each keeps its own secrets file. To switch an existing server from one to the other, copy the secrets from the old file into the new one first, or the services cannot open the existing database.

### 3.5 Publishing a new version (maintainers)

Build and push every image from the repository root. Set `VERSION`, and bump `version` in `frontend/package.json` to match:

```bash
REPO=vishaltyagi807/proctor-ai
VERSION=1.1.0
for service in discovery-server api-gateway auth-service user-service department-service role-service \
               complaint-service storage-service custom-field-service notification-service monitor-service; do
  docker build --target service --build-arg SERVICE=$service --build-arg GRADLE_FLAGS=--no-build-cache \
    -t $REPO:$service-$VERSION -t $REPO:$service-latest backend
done
docker build --target face-service --build-arg SERVICE=face-service --build-arg GRADLE_FLAGS=--no-build-cache \
  -t $REPO:face-service-$VERSION -t $REPO:face-service-latest backend
docker build --target prod -t $REPO:frontend-$VERSION -t $REPO:frontend-latest frontend
docker push --all-tags $REPO
```

Servers then move to it with `./setup.sh update <version>`. Database changes go in `backend/init/`. Running `docker/setup.sh` inside the repository copies them into `docker/init/` automatically.

---

## 4. Development

### 4.1 Start the development stack

```bash
./setup.sh
```

Make sure Docker is running first (step 2.2). On Windows, run this in Git Bash. The first run takes a few minutes. Setup does the following:

1. Creates `backend/.env` with new random secrets
2. Checks Docker and Compose
3. Downloads the face recognition models from Hugging Face (about 190 MB, once) and verifies their checksums
4. Checks that the ports are free
5. Builds and starts every container, then waits until all of them are healthy

When it finishes it prints:

| Service | URL |
|---|---|
| Web app | http://localhost:5173 |
| API gateway | http://localhost:7050 |
| Service registry | http://localhost:8761 (user `proctor-discovery`, password is `DISCOVERY_PASSWORD` in `backend/.env`) |
| MinIO console | http://localhost:9201 (`MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD` in `backend/.env`) |

Sign in with the seeded administrator `admin@college.com`. Its password is in `backend/init/07_seed.sql`.

### 4.2 Everyday workflow

| Task | What to do |
|---|---|
| Change frontend code | Save the file. The browser reloads instantly. |
| Change backend code | Run `./setup.sh` again. It rebuilds only the changed images and updates the stack in place. |
| Follow logs | `./setup.sh logs` or `./setup.sh logs user-service` |
| Check health | `./setup.sh status` |
| Stop for the day | `./setup.sh stop` (all data is kept) |
| Remove containers | `./setup.sh down` (all data is kept) |
| Start over with an empty database | `./setup.sh purge`, then `./setup.sh` |

### 4.3 Database changes

The schema, row-level security policies, functions and seed data live in `backend/init/`. PostgreSQL applies them **only when the database volume is created**. The project has no migration files.

To apply a change in development:

```bash
./setup.sh purge     # deletes all development data, asks to confirm
./setup.sh
```

### 4.4 Ports and allowed origins

Every host port can be changed if something else already uses it:

```bash
FRONTEND_PORT=5174 GATEWAY_SERVER_PORT=7051 ./setup.sh
```

Setup manages `APP_ALLOWED_ORIGINS` on every run. To allow another site (for example an admin tool on another port), add it to `APP_EXTRA_ORIGINS` in `backend/.env` instead, comma separated, and run `./setup.sh` again. Setup validates these origins and refuses the file storage and service registry addresses on purpose.

### 4.5 Testing the API

Import `backend/postman/ProctorAI.postman_collection.json` and `backend/postman/ProctorAI.postman_environment.json` into Postman. Every request goes through the gateway at http://localhost:7050.

### 4.6 Running the frontend outside Docker (optional)

```bash
cd frontend
npm ci
npm run dev
```

This serves http://localhost:5173 and proxies `/api` to the gateway on port 7050. Stop the Docker frontend first so the port is free: `docker compose -f backend/docker-compose.yaml stop frontend`.

---

## 5. Production from source

### 5.1 Deploy

On the server:

```bash
./setup.sh prod
```

Setup asks for:

| Prompt | Example | Serves |
|---|---|---|
| Web app domain | `app.example.com` | The web app and its API |
| File storage domain | `files.example.com` | Uploads and downloads |
| Service registry domain | `registry.example.com` | The service registry dashboard, behind a password |
| Email for TLS certificates | `ops@example.com` | Let's Encrypt expiry notices |

Leave a domain empty to serve that part from the server IP address. Then setup does the following:

1. Creates `backend/.env.production` with new random secrets
2. Detects the public IP address and checks each domain in public DNS
3. Configures the Caddy edge proxy, CORS origins, cookies and file URLs to match
4. Builds and starts the hardened production stack
5. Waits until every TLS certificate is active
6. Prints the URLs and the DNS records that are still missing

### 5.2 DNS records

Add these at your DNS provider. Setup prints them with your real IP address and the status of each one.

| Type | Name | Value |
|---|---|---|
| A | `app.example.com` | server public IPv4 |
| A | `files.example.com` | server public IPv4 |
| A | `registry.example.com` | server public IPv4 |
| CAA (optional) | `example.com` | `0 issue "letsencrypt.org"` |
| CAA (optional) | `example.com` | `0 issue "sectigo.com"` (allows the ZeroSSL fallback) |

Do not add AAAA (IPv6) records unless the server really answers on that address. Certificate validation tries IPv6 first and fails otherwise.

### 5.3 Before and after DNS

The app is usable right away, from the IP address:

| Part | Until DNS points to the server | After DNS |
|---|---|---|
| Web app | `http://<server-ip>` | `https://app.example.com` |
| File storage | `http://<server-ip>:9200` | `https://files.example.com` |
| Service registry | SSH tunnel to `http://localhost:8761` | `https://registry.example.com` |
| MinIO console | SSH tunnel to `http://localhost:9201` | SSH tunnel to `http://localhost:9201` |

The IP stage is plain HTTP and meant only for the time until DNS is ready. To switch to HTTPS:

```bash
./setup.sh dns          # shows each record and whether it is in place
./setup.sh prod --yes   # switches to HTTPS once the records are in place
```

The service registry and the MinIO console are never exposed over plain HTTP. Reach them through an SSH tunnel:

```bash
ssh -L 8761:127.0.0.1:8761 -L 9201:127.0.0.1:9201 <user>@<server-ip>
```

### 5.4 Unattended installs

```bash
APP_DOMAIN=app.example.com \
FILES_DOMAIN=files.example.com \
REGISTRY_DOMAIN=registry.example.com \
ACME_EMAIL=ops@example.com \
./setup.sh prod --yes
```

Add `PUBLIC_IP=<address>` if the server cannot detect its own public address.

### 5.5 Configuration

All production settings live in `backend/.env.production`, which setup creates with `600` permissions. Keep a copy somewhere safe: the secrets in it decrypt nothing on their own, but they are needed to reuse the existing database.

| Setting | Managed by | Notes |
|---|---|---|
| Secrets (database, JWT, Redis, MinIO, registry) | Setup, once | Never changed after creation |
| `APP_DOMAIN`, `FILES_DOMAIN`, `REGISTRY_DOMAIN`, `ACME_EMAIL`, `PUBLIC_IP` | Setup prompts | Rerun `./setup.sh prod` to change them |
| `APP_ALLOWED_ORIGINS`, `MINIO_CORS_ALLOW_ORIGIN`, `S3_PUBLIC_ENDPOINT`, `APP_COOKIE_SECURE` | Setup, every run | Do not edit by hand |
| `APP_EXTRA_ORIGINS` | You | Extra sites allowed to call the API, comma separated |
| `FACE_MATCH_THRESHOLD`, `FACE_DETECTION_SCORE_THRESHOLD` | You | Face recognition tuning |
| `APP_STORAGE_TEMP_TTL`, `APP_STORAGE_SWEEP_INTERVAL` | You | How long temporary import files may remain (defaults `30m` and `15m`) |

After editing the file, run `./setup.sh prod --yes` to apply it.

### 5.6 Operating the stack

```bash
./setup.sh status --prod
./setup.sh logs --prod                 # all services
./setup.sh logs --prod edge            # TLS and access logs
./setup.sh stop --prod
./setup.sh down --prod
```

### 5.7 Updating to a new version

Copy the new code to the server, then run:

```bash
./setup.sh prod --yes
```

Setup rebuilds the images and replaces the containers. The data volumes and certificates are kept.

Changes in `backend/init/` do **not** reach an existing production database, because the init scripts run only when the database is first created. Apply such changes to production by hand with `psql` before deploying code that needs them.

### 5.8 Backups

Back up the database and the uploaded files regularly:

```bash
PROD="docker compose -f backend/docker-compose.prod.yaml --env-file backend/.env.production"

$PROD exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > proctor-$(date +%F).dump

docker run --rm -v proctor-prod_minio_data:/data:ro -v "$PWD":/backup alpine \
  tar czf /backup/minio-$(date +%F).tgz -C /data .
```

To restore the database into a running stack:

```bash
$PROD exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < proctor-YYYY-MM-DD.dump
```

### 5.9 Removing everything

```bash
./setup.sh purge --prod
```

This deletes the database, uploads, face enrollments and TLS certificates, and asks for confirmation first. Let's Encrypt limits how often the same certificate can be issued again, so avoid purging production repeatedly.

---

## 6. Desktop and mobile apps

All commands in this section run inside `frontend/`:

```bash
cd frontend
npm ci
```

### 6.1 How the apps reach your server

The apps talk to a ProctorAI server set up with `./setup.sh`. There are two ways to point them at it:

- **Build it in.** Set `VITE_SERVER_URL` when building, for example `VITE_SERVER_URL=https://app.example.com npm run tauri:build`.
- **Let users choose.** Without `VITE_SERVER_URL`, the sign-in screen asks for the server address and checks it before saving.

Setup already allows the app origins (`tauri://localhost`, `http://tauri.localhost`, `https://tauri.localhost`), so no server changes are needed. Use the HTTPS domain for app users: Android release builds block plain-HTTP uploads.

### 6.2 Desktop: development

Run the backend with `./setup.sh`, free port 5173, then start the desktop app:

```bash
docker compose -f ../backend/docker-compose.yaml stop frontend
npm run tauri:dev
```

The app opens in a native window and reloads on every frontend change. It signs in against your local stack. Start the Docker frontend again afterwards with `docker compose -f ../backend/docker-compose.yaml start frontend`.

### 6.3 Desktop: release builds

```bash
VITE_SERVER_URL=https://app.example.com npm run tauri:build
```

The installers land in `src-tauri/target/release/bundle/`:

| Built on | Output | Folder |
|---|---|---|
| Linux | `.deb`, `.rpm`, `.AppImage` | `bundle/deb`, `bundle/rpm`, `bundle/appimage` |
| Windows | `.msi`, `.exe` setup | `bundle/msi`, `bundle/nsis` |
| macOS | `.app`, `.dmg` | `bundle/macos`, `bundle/dmg` |

For a single macOS build that runs on both Intel and Apple Silicon:

```bash
rustup target add aarch64-apple-darwin x86_64-apple-darwin
npm run tauri:build -- --target universal-apple-darwin
```

**Signing for distribution**

| Platform | Without signing | To sign |
|---|---|---|
| Windows | SmartScreen warns on first launch | Configure a code signing certificate: https://v2.tauri.app/distribute/sign/windows/ |
| macOS | Runs on the build Mac; Gatekeeper blocks it elsewhere | Set `APPLE_SIGNING_IDENTITY`, plus `APPLE_ID`, `APPLE_PASSWORD` and `APPLE_TEAM_ID` to notarize: https://v2.tauri.app/distribute/sign/macos/ |
| Linux | Installs normally | Optional |

### 6.4 Android

**Set up once:**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export NDK_HOME="$ANDROID_HOME/ndk/<version>"
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
npm run tauri:android:init
npm run icons
```

`tauri:android:init` creates `src-tauri/gen/android` and adds the camera permission. `npm run icons` then copies the adaptive app icons into it.

**Develop on a phone or emulator:**

```bash
npm run tauri:android:dev
```

The phone loads the app from your computer over the local network. Allow that address once, so the backend accepts it: add `APP_EXTRA_ORIGINS=http://<your-computer-lan-ip>:5173` to `backend/.env` and run `./setup.sh` again.

**Sign release builds.** Without signing, release APKs do not install. Create a keystore once and keep it outside the repository. Losing it means you cannot publish updates.

```bash
keytool -genkey -v -keystore ~/proctorai-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Create `src-tauri/gen/android/keystore.properties` (never commit it):

```properties
password=<keystore password>
keyAlias=upload
storeFile=/home/<you>/proctorai-upload.jks
```

In `src-tauri/gen/android/app/build.gradle.kts`, add `import java.io.FileInputStream` at the top, then add this inside the `android { }` block, above `buildTypes`:

```kotlin
signingConfigs {
    create("release") {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val keystoreProperties = java.util.Properties()
        if (keystorePropertiesFile.exists()) {
            keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        }
        keyAlias = keystoreProperties["keyAlias"] as String
        keyPassword = keystoreProperties["password"] as String
        storeFile = file(keystoreProperties["storeFile"] as String)
        storePassword = keystoreProperties["password"] as String
    }
}
```

Inside `buildTypes { getByName("release") { … } }`, add `signingConfig = signingConfigs.getByName("release")`.

**Build:**

```bash
VITE_SERVER_URL=https://app.example.com npm run tauri:android:build -- --apk   # APK to install directly
VITE_SERVER_URL=https://app.example.com npm run tauri:android:build -- --aab   # AAB for Google Play
```

The outputs land in `src-tauri/gen/android/app/build/outputs/`, under `apk/universal/release/` and `bundle/universalRelease/`. The build prints the exact paths.

### 6.5 iOS (on a Mac)

**Set up once:**

```bash
brew install cocoapods
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios
npm run tauri:ios:init
npm run icons
```

Device and App Store builds need an Apple development team. Set `APPLE_DEVELOPMENT_TEAM`, or `bundle.iOS.developmentTeam` in `src-tauri/tauri.conf.json`.

**Develop:**

```bash
npm run tauri:ios:dev                  # simulator
npm run tauri:ios:dev -- --open        # open the project in Xcode
```

**Build:**

```bash
VITE_SERVER_URL=https://app.example.com npm run tauri:ios:build
```

The build prints the path of the `.ipa`. Upload it to App Store Connect with Xcode or Transporter.

### 6.6 What the apps support

| Feature | Behaviour |
|---|---|
| Sign-in | Bearer tokens. The refresh token is stored in the app and rotated on each refresh. |
| Live updates | Notifications, background jobs and the system monitor stream in real time. |
| Notifications | System notifications while the app is in the background |
| Uploads and downloads | Uploads show progress. Desktop downloads open a save dialog; phones use the system viewer. |
| Camera | Face recognition with the device camera |
| Deep links | `proctorai://complaints/<id>` opens that complaint. On desktop, a second launch focuses the open window. |
| Safe areas | Content stays clear of notches, rounded corners and system bars. |

### 6.7 App icons

The logo sources are in `frontend/brand/`. After changing them, run:

```bash
npm run icons
```

This regenerates every desktop, Android, iOS and web icon. Run it again after `tauri:android:init` or `tauri:ios:init`.

---

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| `These ports are already in use` | Stop the program using the port, or choose another one, for example `FRONTEND_PORT=5174 ./setup.sh` |
| `Could not download face-detector.onnx` (or `face-embedder.onnx`) | Check the internet connection and run `./setup.sh` again. On a server without internet, download both files from the links in `backend/face-service/README.md` into `backend/face-service/models/` |
| A service stays unhealthy | `./setup.sh logs <service>` shows the error. `./setup.sh status` lists every container. |
| Backend changes do not show up | Run `./setup.sh` again. Only the frontend reloads automatically. |
| Database changes do not apply | Init scripts run only on a new database. Use `./setup.sh purge` in development. |
| HTTPS does not come up in production | Check that TCP 80 and 443 are open and that `./setup.sh dns` shows every record pointing to the server. Then check `./setup.sh logs --prod edge`. |
| Browser shows a CORS error | Add the site to `APP_EXTRA_ORIGINS` and rerun setup. Never add the file storage or registry address. |
| `npm run tauri:dev` says port 5173 is in use | Stop the Docker frontend: `docker compose -f backend/docker-compose.yaml stop frontend` |
| Android build says `NDK_HOME` is not set | `export NDK_HOME="$ANDROID_HOME/ndk/<version>"`, or run `./native-doctor.sh android` for the exact path |
| Release APK will not install | Configure release signing (section 6.4). |
| The app cannot reach the server | Check the address on the sign-in screen. Use the HTTPS domain, and make sure `./setup.sh prod` ran after the app origins were added. |
| Something else is missing for a native build | `./native-doctor.sh` |

---

## 8. Command reference

### `setup.sh` (build from source)

| Command | Does |
|---|---|
| `./setup.sh` | Set up and start the development stack |
| `./setup.sh prod` | Set up and start the production stack (asks for domains) |
| `./setup.sh prod --yes` | Same, keeping the saved domains without asking |
| `./setup.sh env [--prod]` | Only create the env file with new random secrets |
| `./setup.sh dns` | Show the production DNS records and whether they are in place |
| `./setup.sh status [--prod]` | Show every container and its health |
| `./setup.sh logs [--prod] [service]` | Follow logs |
| `./setup.sh stop [--prod]` | Stop the containers, keep all data |
| `./setup.sh down [--prod]` | Remove the containers, keep all data |
| `./setup.sh purge [--prod] [--yes]` | Remove the containers and all data |

### `docker/setup.sh` (published images)

| Command | Does |
|---|---|
| `./setup.sh` | Configure and start the stack from Docker Hub |
| `./setup.sh update [version]` | Pull the images (optionally another version) and restart |
| `./setup.sh env` | Only create `.env` with new random secrets |
| `./setup.sh dns` | Show the DNS records and whether they are in place |
| `./setup.sh status` | Show every container and its health |
| `./setup.sh logs [service]` | Follow logs |
| `./setup.sh stop` / `down` | Stop or remove the containers, keep all data |
| `./setup.sh purge [--yes]` | Remove the containers and all data |

### `native-doctor.sh`

| Command | Does |
|---|---|
| `./native-doctor.sh` | Check desktop, Android and iOS build prerequisites |
| `./native-doctor.sh desktop` | Check the desktop build for this OS |
| `./native-doctor.sh android` | Check Android builds |
| `./native-doctor.sh ios` | Check iOS builds |

### `npm` scripts in `frontend/`

| Script | Does |
|---|---|
| `npm run dev` | Vite dev server for the web app |
| `npm run build` | Production web build |
| `npm run lint` | ESLint |
| `npm run icons` | Regenerate all icons from `brand/` |
| `npm run tauri:dev` | Desktop app in development |
| `npm run tauri:build` | Desktop installers for this OS |
| `npm run tauri:android:init` | Create the Android project |
| `npm run tauri:android:dev` | Android app in development |
| `npm run tauri:android:build` | Android APK or AAB |
| `npm run tauri:ios:init` | Create the iOS project |
| `npm run tauri:ios:dev` | iOS app in development |
| `npm run tauri:ios:build` | iOS IPA |
