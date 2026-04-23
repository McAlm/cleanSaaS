# cleanSaaS

A simple CLI tool to delete process definitions from your **Camunda SaaS** cluster. Run it once, and it exits — no web server, no hassle.

## What It Does

cleanSaaS connects to your Camunda Orchestration Cluster via REST API and deletes process definitions by ID. Optionally, it can cascade-delete all running and historic process instances associated with those definitions.

**Use cases:**
- Remove old or test process definitions from your cluster
- Clean up deployed processes during development/testing
- Bulk delete multiple process definitions at once

## Prerequisites

Before you start, make sure you have:

- **Java 21** or later ([download](https://www.oracle.com/java/technologies/downloads/))
- **Maven 3.8+** ([download](https://maven.apache.org/))
- **Camunda SaaS account** with a cluster deployed
- **API credentials** (OAuth2 client ID and secret) from Camunda Console

## Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd cleanSaaS
mvn clean package
```

### 2. Configure

Copy the configuration template:

```bash
cp src/main/resources/application.yaml.template src/main/resources/application.yaml
```

Edit `src/main/resources/application.yaml` with your Camunda credentials and target process IDs (see [Configuration](#configuration) below).

### 3. Run

```bash
mvn spring-boot:run
```

The tool will execute once and exit. Check the console output for status and any errors.

---

## Configuration

All settings live in `src/main/resources/application.yaml`. Here's what you need to set:

### Camunda Cluster Connection

```yaml
camunda:
  client:
    rest-address: https://<region>.zeebe.camunda.io/<cluster-id>
    auth:
      client-id: <your-client-id>
      client-secret: <your-client-secret>
```

**How to find these values:**

1. Go to [Camunda Console](https://console.camunda.io/)
2. Select your cluster
3. Navigate to **API** tab
4. Copy the **REST API URL** → use for `rest-address`
5. Click **Create new client** (or use existing) → copy **Client ID** and **Client Secret**

### Process Definition IDs to Delete

```yaml
    cleanup:
      process-definition-ids:
        - order-process
        - payment-workflow
        - legacy-process
```

**To delete ALL process definitions**, leave this list empty or comment it out:

```yaml
    cleanup:
      process-definition-ids: []
```

### Cascade Delete (Optional)

Delete not just the process definition, but also all running and historic instances:

```yaml
    cleanup:
      cascade: true  # WARNING: This is irreversible!
```

Default is `false` — only process definitions are deleted.

### Logging (Optional)

Control verbosity:

```yaml
logging:
  level:
    io.cleansaas: DEBUG  # INFO, DEBUG, or WARN
```

---

## Usage Examples

### Example 1: Delete Specific Process Definitions

**application.yaml:**
```yaml
spring:
  main:
    web-application-type: none

camunda:
  client:
    rest-address: https://us-2.zeebe.camunda.io/12345abc
    auth:
      client-id: myApp~myCluster~user1
      client-secret: xxxxxxxxxxxxxxxx
    cleanup:
      process-definition-ids:
        - invoice-processing
        - approval-workflow
      cascade: false
```

**Run:**
```bash
mvn spring-boot:run
```

**Output:**
```
Found 2 process definitions to delete:
  • invoice-processing
  • approval-workflow

[OK] Deleted invoice-processing
[OK] Deleted approval-workflow

Cleanup complete. 2 process definitions deleted.
```

### Example 2: Delete All Process Definitions (Cascade)

```yaml
camunda:
  client:
    rest-address: https://us-2.zeebe.camunda.io/12345abc
    auth:
      client-id: myApp~myCluster~user1
      client-secret: xxxxxxxxxxxxxxxx
    cleanup:
      process-definition-ids: []  # Empty = delete ALL
      cascade: true               # Also delete running instances
```

---

## How It Works

1. **Authentication**: Uses OAuth2 client credentials to obtain a bearer token
2. **Search**: Queries Camunda REST API for process definitions (paginated, 50 per page)
3. **Delete**: Deletes each process definition by ID
4. **Cascade** (optional): If enabled, also deletes all associated process instances
5. **Exit**: Logs final status and exits (no background processes)

The tool is **idempotent** — if a process definition doesn't exist, it's safely skipped.

---

## Build & Test

### Build

```bash
mvn clean package
```

Generates an executable JAR in `target/cleanSaaS-1.0.0-SNAPSHOT.jar`.

### Run Tests

```bash
mvn test
```

Run a specific test class:

```bash
mvn test -Dtest=ProcessDefinitionServiceTest
```

Run a specific test method:

```bash
mvn test -Dtest=ProcessDefinitionServiceTest#testDeleteProcessDefinition
```

---

## Troubleshooting

### "Unauthorized" or "403 Forbidden"

- ✓ Check `client-id` and `client-secret` are correct
- ✓ Verify OAuth2 client has required scopes: **Zeebe** (for delete) and **Operate** (for search)
- ✓ Ensure token isn't expired (tokens are automatically refreshed)

### "Process definition not found"

- ✓ The process ID might not exist or is already deleted (this is OK — it's skipped)
- ✓ Use Camunda Console to verify the exact process ID

### "No process definitions deleted"

- ✓ Check that `process-definition-ids` list is not empty
- ✓ Verify each ID matches exactly (case-sensitive)

### Connection timeout / SSL errors

- ✓ Verify `rest-address` URL is correct
- ✓ Check your network/firewall allows outbound HTTPS to Camunda
- ✓ Try increasing timeout: add `spring.mvc.async.request-timeout: 60000` to `application.yaml`

### Cascade delete didn't work

- ✓ Ensure `cascade: true` is set
- ✓ Only works on process definitions; individual instance deletion is not supported

### Enable Debug Logging

```yaml
logging:
  level:
    io.cleansaas: DEBUG
    org.springframework.web.client: DEBUG
```

This shows detailed HTTP requests, responses, and OAuth2 token exchanges.

---

## Architecture

```
CleanupRunner
    ↓
ProcessDefinitionService (REST API calls)
    ↓
OAuthTokenService (OAuth2 token management)
    ↓
Camunda REST API
```

- **CleanupRunner**: Entry point; orchestrates the cleanup workflow
- **ProcessDefinitionService**: Handles search and delete API calls; manages pagination
- **OAuthTokenService**: Fetches and caches OAuth2 tokens; auto-refreshes 30 seconds before expiry
- **CamundaProperties**: Type-safe configuration from `application.yaml`

---

## Environment Variables (Advanced)

You can override settings via environment variables:

```bash
export CAMUNDA_CLIENT_REST_ADDRESS=https://us-2.zeebe.camunda.io/12345abc
export CAMUNDA_CLIENT_AUTH_CLIENT_ID=myApp~myCluster~user1
export CAMUNDA_CLIENT_AUTH_CLIENT_SECRET=xxxxxxxxxxxxxxxx
export CAMUNDA_CLIENT_CLEANUP_CASCADE=true

mvn spring-boot:run
```

---

## Security Notes

- **Never commit credentials** to version control. Use `application.yaml.template` as a guide and add `application.yaml` to `.gitignore`
- **Client secrets should be treated like passwords** — use secure secret management (e.g., environment variables, HashiCorp Vault, AWS Secrets Manager)
- **Cascade delete is irreversible** — use with caution in production

---

## Support

For issues with the tool or questions:

- Check the **Troubleshooting** section above
- Review logs with `DEBUG` level enabled
- Consult [Camunda REST API docs](https://docs.camunda.io/docs/apis-tools/public-api/operate-api/overview/)

---

## License

[Add your license here]

---

## Version

**cleanSaaS** `1.0.0-SNAPSHOT`  
Built with Spring Boot 3.4.4, Java 21
