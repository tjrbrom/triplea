# Developer Setup Guide

## Before Getting Started
- Install JDK 11 (project is on this Java version)
- [Install IDE](./how-to/ide-setup) (favor choosing IDEA)
  - Create as a gradle project (file > open project > select the build.gradle file))
  - Usually TripleA and lobby are started from within IDE, look for checked in 'run configurations'.
- Install docker
  - Docker for Mac can be obtained at: <https://store.docker.com/editions/community/docker-ce-desktop-mac>

## Getting Started

- Fork <https://github.com/triplea-game/triplea>
- Clone your newly forked repository
- Create a new branch in your fork repository and do the checkout (see the [typical workflow](./how-to/typical-git-workflow.md))
- Follow TripleA's [pull requests process here](../reference/dev-process/pull-requests.md).

## Compile and launch TripleA
```bash
./gradlew :game-app:game-headed:run
```

## Launch Local Database

```bash
./spitfire-server/database/start_docker_db
```

This will:
- start a postgres DB docker container
- run flyway to install a database schema
- load a sample 'SQL' file to populate a small sample dataset

### Working with database

```
## connect to database to bring up a SQL CLI
./spitfire-server/database/connect_to_docker_db

## connect to lobby database
\c lobby_db

## list tables
\d

## exit SQL CLI
\q

## erase database and recreate with sampledrop database schema & data and recreate
./spitfire-server/database/reset_docker_db
```

## Launch local lobby:

```bash
./gradlew :spitfire-server:dropwizard-server:run
```

To connect to local lobby, from the game client:
  - 'settings > testing > local lobby'
  - play online
  - use 'test:test' to login to local lobby as a moderator

## Run all checks and tests

```bash
./verify
```

## Run formatting:

```
./format
```

## Reference

For more detailed steps on building the project, see:
- [how-to/cli-build-commands.md](./how-to/cli-build-commands.md)
- [how-to/typical-git-workflow.md](./how-to/typical-git-workflow.md)

## Coding Style Guide & Expectations

- Code formatting is google java format
- Checkstyle will verify low level code style standards
- Generally write tests for new and modified code

Full list of coding conventions can be found at: [reference/code-conventions](./reference/code-conventions)

# Pitfalls and Pain Points to be aware of

## Save-Game Compatibility

- Do not rename private fields or delete private fields of anything that extends `GameDataComponent`

Game saves are done via object serialization that is then written to file. Renaming or deleting
fields will prevent previous save games from loading.

## Network Compatibility

'@RemoteMethod' indicates methods invoked over network. The API of these methods may not change.

## Lots of Manual Testing Required

A lot of code is not automatically verified via tests, any change should be tested pretty
thoroughly and for a variety of maps and scenarios. This can be very time consuming for
even the smallest of changes.

