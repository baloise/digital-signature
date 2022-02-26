# Simple Confluence Setup

```bash
docker run --name=confluence -d -p 8090:8090 -p 8091:8091 atlassian/confluence-server:latest
docker run --name postgres -e POSTGRES_PASSWORD=mysecretpassword -d postgres

docker inspect postgres # to get IP
```

Start confluence setup
- add a new license
- and configure Postgres connection:
  - hostname: IP from docker inspect
  - port: 5432
  - db: postgres
  - user: postgres
  - password: `mysecretpassword` (defined above)
- Skip tutorial
- Create new space "Test"
