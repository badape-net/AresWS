# Ares Web Services

Provides a Game Management Service currently integrates with steam web api

## Features

 * Create New Players
 * Buy Heroes
 * List Player Rosters

## Usage Notes

to bring up development environment

```
docker-compose up
docker-compose -f docker-compose-liquibase.yml build
docker-compose -f docker-compose-liquibase.yml up
docker-compose -f docker-compose-liquibase.yml stop
curl http://localhost:8765/store/refresh
```

this will bring up postgres and pgadmin and then run the liquidbase scripts

to start the service

```
mvn package exec:java -DskipTests=true
```

to package the docker image

```
mvn clean package docker:build -DskipTests=true
```
