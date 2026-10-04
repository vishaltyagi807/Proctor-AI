# ProctorAI: deploy from Docker Hub

This folder runs the complete ProctorAI production stack from the prebuilt images in
[`vishaltyagi807/proctor-ai`](https://hub.docker.com/r/vishaltyagi807/proctor-ai) on Docker Hub. It works on its
own: copy this folder to any server with Docker 25+ and Compose 2.21+. No source code, Java,
Node.js or model downloads are needed.

| File | Purpose |
|---|---|
| `setup.sh` | Creates `.env`, configures domains and HTTPS, pulls the images and runs the stack |
| `docker-compose.yaml` | The hardened production stack |
| `init/` | Database schema, security policies and seed data, applied when the database is first created |

## Start

```bash
./setup.sh
```

Setup does the following:

1. Creates `.env` with new random secrets (keep a copy somewhere safe)
2. Asks for the web app, file storage and service registry domains, and an email for TLS certificates
3. Detects the public IP address, checks DNS and configures the Caddy edge proxy
4. Pulls the images and starts every container, then waits until all of them are healthy
5. Replaces the default administrator password with a random one and shows it once
6. Prints the URLs and the DNS records that are still missing

If the repository is private, run `docker login` first.

## Commands

| Command | Does |
|---|---|
| `./setup.sh` | Configure and start (rerun it after DNS is ready to switch to HTTPS) |
| `./setup.sh update` | Pull the images again and restart, keeping all data |
| `./setup.sh update 1.1.0` | Switch to another version |
| `./setup.sh dns` | Show the DNS records and whether they are in place |
| `./setup.sh status` | Show every container and its health |
| `./setup.sh logs [service]` | Follow logs, for example `./setup.sh logs edge` |
| `./setup.sh stop` / `down` | Stop or remove the containers, keeping all data |
| `./setup.sh purge` | Delete the containers and all data, including certificates (asks to confirm) |

Add `--yes` to skip prompts.

## DNS and firewall

Point an A record for each domain at the server, and open TCP 80, TCP 443 and UDP 443. Also open
TCP 9200 until DNS is ready, because file storage is served from that port until then. Until DNS
points to the server, the web app runs from `http://<server-ip>`. The full walkthrough is in
`GUIDE.md` in the source repository.
