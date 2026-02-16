# Local MQ Container

--8<-- "development/local-mq-container.md"

## Java-specific notes

### Port offsets

The Java repo uses offset ports (9453/9454, 1424/1425) rather than the
standard ports (9443/9444, 1414/1415) used by the Python and Go repos.
This allows running integration tests for multiple repos simultaneously
without port conflicts.

| Setting | QM1 | QM2 |
| --- | --- | --- |
| MQ listener port | `1424` | `1425` |
| REST API port | `9453` | `9454` |
| REST base URL | `https://localhost:9453/ibmmq/rest/v2` | `https://localhost:9454/ibmmq/rest/v2` |

### Running integration tests

```bash
# Start MQ and seed configuration
scripts/dev/mq_start.sh
scripts/dev/mq_seed.sh

# Run integration tests
MQ_REST_ADMIN_RUN_INTEGRATION=1 ./mvnw verify

# Stop MQ when done
scripts/dev/mq_stop.sh
```

### Environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `MQ_REST_BASE_URL` | `https://localhost:9453/ibmmq/rest/v2` | QM1 REST API base URL |
| `MQ_REST_BASE_URL_QM2` | `https://localhost:9454/ibmmq/rest/v2` | QM2 REST API base URL |
| `MQ_REST_ADMIN_RUN_INTEGRATION` | (unset) | Set to `1` to enable integration tests |

### Gateway routing example

```java
var session = MqRestSession.builder()
    .host("localhost")
    .port(9453)
    .queueManager("QM2")
    .credentials(new LtpaAuth("mqadmin", "mqadmin"))
    .gatewayQmgr("QM1")
    .verifyTls(false)
    .build();

var qmgr = session.displayQmgr();
// QM2's attributes, routed through QM1
```
