## Docker Setup (Colima on macOS)

### Prerequisites

```bash
brew install colima docker docker-compose
```

### Start Colima

```bash
colima start --memory 5 --disk 50
```

After reboot, run `colima start` again.

### Manual Setup

```bash
# Create volumes
docker volume create ds-pgdata
docker volume create ds-confdata

# Create network
docker network create ds-net

# Start PostgreSQL
docker run --name ds-postgres \
  --network ds-net \
  -v ds-pgdata:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=mysecretpassword \
  -d postgres:17

# Start Confluence
docker run --name ds-confluence \
  --network ds-net \
  -v ds-confdata:/var/atlassian/application-data/confluence \
  -p 8090:8090 \
  -p 8091:8091 \
  -p 5005:5005 \
  -d \
  -e JVM_MINIMUM_MEMORY=1536m \
  -e JVM_MAXIMUM_MEMORY=1536m \
  -e JVM_SUPPORT_RECOMMENDED_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Datlassian.upm.signature.check.disabled=true -Dupm.plugin.upload.enabled=true" \
  atlassian/confluence:latest
```

### Confluence Setup

Configure the database connection in the setup wizard:
- JDBC URL: `jdbc:postgresql://ds-postgres:5432/postgres`
- User: `postgres`
- Password: `mysecretpassword`

Access Confluence at `http://localhost:8090`.

### Automated Testing

Use `scripts/test-plugin.sh` for testing against specific Confluence versions:

```bash
# Build and test against Confluence 9 and 10
scripts/test-plugin.sh build 9.5.4
scripts/test-plugin.sh build 10.2.7

scripts/test-plugin.sh start 9.5.4      # Starts on port 9090
scripts/test-plugin.sh start 10.2.7     # Starts on port 10090

scripts/test-plugin.sh upload 9.5.4
scripts/test-plugin.sh upload 10.2.7

scripts/test-plugin.sh teardown 9.5.4
scripts/test-plugin.sh teardown 10.2.7
```

### Useful Commands

```bash
docker ps                    # Running containers
docker logs -f ds-confluence # Follow logs
docker stop ds-confluence ds-postgres
docker start ds-confluence ds-postgres
docker rm -f ds-confluence ds-postgres
docker network rm ds-net
docker volume rm ds-pgdata ds-confdata
```
