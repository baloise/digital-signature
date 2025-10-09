## Setup Script for Podman on macOS:

```bash
# Create named Podman volumes
podman volume create postgres-data
podman volume create confluence-data

# Create a pod to allow containers to communicate via localhost
podman pod create --name confluence-pod \
  -p 8090:8090 \
  -p 8091:8091 \
  -p 5005:5005

# Run postgres in the pod
podman run --name postgres \
  --pod confluence-pod \
  -v postgres-data:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=mysecretpassword \
  -d postgres

# Run confluence in the pod with appropriate memory settings
podman run --name=confluence \
  --pod confluence-pod \
  -v confluence-data:/var/atlassian/application-data/confluence \
  -d \
  -e JVM_MINIMUM_MEMORY=1536m \
  -e JVM_MAXIMUM_MEMORY=1536m \
  -e JVM_SUPPORT_RECOMMENDED_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  atlassian/confluence-server:latest
```

## Confluence Setup Instructions:

**Configure Postgres database connection:**
* JDBC URL: `jdbc:postgresql://localhost:5432/postgres`
* User: `postgres`
* Password: `mysecretpassword`

**Access Confluence:**
* Open your browser to `http://localhost:8090`
* Follow the setup wizard and use the database credentials above

## Useful Commands:

```bash
# View running containers
podman ps

# View all containers (including stopped)
podman ps -a

# View logs
podman logs confluence
podman logs postgres

# Follow logs in real-time
podman logs -f confluence

# Stop everything
podman pod stop confluence-pod

# Start everything
podman pod start confluence-pod

# Restart everything
podman pod restart confluence-pod

# Remove everything (containers and pod, but keeps volumes)
podman pod rm -f confluence-pod

# List volumes
podman volume ls

# Remove volumes (WARNING: deletes all data)
podman volume rm postgres-data confluence-data
```

## Podman Machine Commands (macOS Specific):

On macOS, Podman runs inside a lightweight VM. Here's what you need to know:

### First-time Setup:
```bash
# Initialize the Podman machine with 5GB memory (only once)
podman machine init --memory 5120 --disk-size 50

# Start the machine
podman machine start
```

### If you already have a machine:
```bash
# Stop the existing machine
podman machine stop

# Update memory settings to 5GB
podman machine set --memory 5120

# Start the machine
podman machine start

# Verify settings
podman machine info | grep -i memory
```

### Daily Usage:
```bash
# After each macOS reboot, start the machine
podman machine start

# Check machine status
podman machine list

# Stop the machine (optional, before shutting down Mac)
podman machine stop
```

### When to run machine commands:
- **Once ever**: `podman machine init --memory 5120` (initial setup)
- **After each reboot**: `podman machine start`
- **Between container operations**: NOT needed - machine stays running
- **Normal development**: Just use `podman` commands, ignore the machine

### Optional - Auto-start on boot:
```bash
podman machine set --autostart=true
```

### Common machine commands:
```bash
# View machine info
podman machine info

# SSH into the machine (for troubleshooting)
podman machine ssh

# Remove machine (clean slate)
podman machine stop
podman machine rm
```

## Installation:

If you haven't installed Podman yet, see the official guide:
**https://podman.io/docs/installation#macos**

Quick install via Homebrew:
```bash
brew install podman
podman machine init --memory 5120 --disk-size 50
podman machine start
```

## Memory Requirements:

- **Podman VM**: 5GB (provides comfortable headroom for Confluence, Postgres, and OS)
- **Confluence JVM**: 1.5GB heap (suitable for small/test instances)
- This configuration supports ~350 users with 15,000 pages according to Atlassian's guidelines

If you experience memory issues, you can increase the VM memory:
```bash
podman machine stop
podman machine set --memory 6144  # Increase to 6GB if needed
podman machine start
```

## Podman vs Docker:

If you're coming from Docker:
- `docker` → `podman` (commands are nearly identical)
- `docker-compose` → `podman-compose` or use pods
- On macOS, both use a VM, but Podman is daemonless
- Podman pods = multiple containers sharing network namespace (like Kubernetes pods)

## Tips for Beginners:

1. **Named volumes** (like `postgres-data`) are managed by Podman and persist data automatically
2. **Pods** group containers together - they share the same network, so they can talk via `localhost`
3. **Machine must be running** before any `podman` commands work on macOS
4. Use `podman ps` frequently to check what's running
5. Logs are your friend: `podman logs <container-name>`
