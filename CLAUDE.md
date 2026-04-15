# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

cleanSaaS is a Spring Boot CLI tool that deletes process definitions from a Camunda SaaS cluster via the Orchestration Cluster REST API. It runs once and exits — there is no embedded web server.

## Commands

```bash
# Build executable JAR
mvn clean package

# Run the tool
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

## Architecture

The application follows a linear execution flow via Spring Boot's `CommandLineRunner`:

1. **`CleanupRunner`** (runner/) — entry point for execution logic; reads target process definition IDs from config and orchestrates search + delete
2. **`ProcessDefinitionService`** (service/) — calls the Camunda REST API (`/v2/process-definitions/search` and delete endpoints); handles pagination using Camunda's `searchAfter` cursor mechanism (50 items per page)
3. **`OAuthTokenService`** (service/) — fetches and caches OAuth2 client credentials tokens; refreshes automatically 30 seconds before expiry
4. **`CamundaProperties`** (config/) — type-safe `@ConfigurationProperties` record with nested `Auth` and `Cleanup` records bound from `application.yaml`
5. **`ProcessDefinition`** (model/) — simple record representing a process definition from the API

## Configuration

All runtime configuration lives in `src/main/resources/application.yaml`. The tool requires:

- `camunda.rest-address` — Camunda cluster REST API base URL
- `camunda.auth.client-id` / `camunda.auth.client-secret` — OAuth2 credentials
- `camunda.cleanup.process-definition-ids` — list of IDs to delete
- `camunda.cleanup.cascade` — whether to also delete running instances (default: false)

All `@NotBlank` fields are validated at startup via Bean Validation — the app will fail fast with clear errors if required config is missing.
