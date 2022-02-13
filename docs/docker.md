Setup following a [tutorial from coffeetime.solutions]( http://coffeetime.solutions/run-atlassian-jira-and-confluence-with-postgresql-on-docker/#Overview_of_series_How_to_run_Jira_and_Confluence_behind_NGINX_reverse_proxy_on_Docker):

```bash
docker run --name=confluence -d -p 8090:8090 -p 8091:8091 atlassian/confluence-server:6.15.9
docker run --name postgres -e POSTGRES_PASSWORD=mysecretpassword -d postgres
```

Start confluence setup and configure Postgres:
- jdbc:postgresql://192.168.65.2:5432/postgres (`docker inspect postgres` to get ip address)
- user: postgres
- password: mysecretpassword (defined above)

Skip tutorial
Create new space "Test"
