# Tier 3 decisions: architecture

## Table of Contents

- [Results](#results)
- [Reasoning](#reasoning)
- [Options not chosen](#options-not-chosen)
- [Dependencies and external constraints](#dependencies-and-external-constraints)
- [References](#references)

## Results

### Decisions made

- **HTTP client library**: `java.net.http.HttpClient` (JDK built-in, zero
  runtime dependencies).
- **JSON library**: Gson (`com.google.code.gson:gson`, single JAR, zero
  transitive dependencies).
- **API surface style**: Method-per-command mirroring pymqrest, with
  `Map<String, Object>` for request/response attributes.
- **Attribute mapping approach**: Direct port of pymqrest's 3-layer mapping
  pipeline (key map, value map, key-value map), with mapping data stored as a
  JSON resource file.
- **Exception hierarchy**: Unchecked (`RuntimeException`), sealed hierarchy
  mirroring pymqrest's structure.

### Implicitly converged decisions

- **Runtime dependency count**: One (Gson). The `java.net.http` client is
  built into the JDK, so library consumers inherit exactly one transitive
  dependency.
- **Naming convention**: `MqRest` prefix for all library types (not `MQREST`).
  Java naming conventions use camel case, and `MQREST` is awkward in camel case
  contexts (`MQRESTException` vs `MqRestException`).
- **Transport abstraction**: `MqRestTransport` interface mirroring pymqrest's
  `MQRESTTransport` Protocol. The JDK `HttpClient` implementation is behind
  this interface, enabling Mockito-based testing of all session logic without
  HTTP calls.
- **Fixed-schema types use Records**: `TransportResponse`, `SyncConfig`,
  `SyncResult`, `EnsureResult`, `MappingIssue`, and credential types
  (`BasicAuth`, `LtpaAuth`, `CertificateAuth`) are Java records. These have
  stable schemas that do not change with MQ versions.
- **Credentials use a sealed interface**: `sealed interface Credentials permits
  BasicAuth, LtpaAuth, CertificateAuth` enables exhaustive pattern matching.

### Deferred decisions

- **Null-safety (JSpecify)**: Still deferred until API surface takes shape.

### Action items

- Add Gson dependency to `pom.xml`.
- Update `CLAUDE.md` Architecture section with decided stack.
- Update `docs/plans/open-decisions.md` to mark architecture items as decided.

## Reasoning

### HTTP client library

#### Key constraints

- pymqrest's entire HTTP surface is a single method: `post_json(url, payload,
  headers, timeout, verify_tls)` returning `(status_code, text, headers)`.
  This is an extremely thin HTTP surface -- a single POST with a JSON body.
- For a library, every runtime dependency is a burden on consumers. Dependency
  conflicts are the most common pain point for Java library users.
- The transport layer must be mockable for testing session logic without
  network calls.

#### Evidence cited

- `java.net.http.HttpClient` (available since Java 11, mature by Java 17)
  covers every feature the project needs:
  - POST with JSON body: `HttpRequest.newBuilder().POST(BodyPublishers
    .ofString(json))`
  - Custom headers: `.header("ibm-mq-rest-csrf-token", token)`
  - Timeout: `.timeout(Duration.ofSeconds(30))`
  - TLS/mTLS: `HttpClient.newBuilder().sslContext(sslContext)` for client
    certificates
- pymqrest's `BasicAuth` manually constructs the `Authorization` header (not
  using requests' built-in auth), so `java.net.http`'s lack of built-in Basic
  auth support is irrelevant.
- pymqrest's `LTPAAuth` manually extracts the `LtpaToken2` cookie from the
  `Set-Cookie` response header, so `java.net.http`'s cookie handling is
  sufficient.

#### Tradeoffs

- `java.net.http`'s API is more verbose than OkHttp's for simple operations.
  This verbosity is absorbed by the `HttpClientTransport` implementation class
  and never exposed to library consumers.
- No built-in connection pooling tuning. Irrelevant for this use case -- the
  library makes infrequent HTTP calls, not high-throughput streaming.

### JSON library

#### Key constraints

- pymqrest uses Python's `json` stdlib and works exclusively with plain
  `dict`/`list` structures. It never uses dataclass serialization, JSON
  schema validation, or streaming. The two operations are: `json.loads(text)`
  to parse a response into a dict, and `json.dumps(dict)` to serialize a
  request.
- The mapping pipeline operates entirely on `Map<String, Object>`. JSON
  parsing feeds into the mapping layer, which transforms maps of attributes
  using dynamic key/value lookups. Typed POJO mapping would not integrate
  with this pipeline.
- Dependency weight matters for a library. Every transitive dependency is a
  potential version conflict for consumers.

#### Evidence cited

- Gson is a single JAR (~280KB) with zero transitive dependencies. Jackson
  requires at minimum `jackson-databind` + `jackson-core` +
  `jackson-annotations` (three JARs, ~2.1MB total), plus optional modules
  for `Optional`, parameter names, etc.
- Gson handles `Map<String, Object>` natively:
  `gson.fromJson(text, new TypeToken<Map<String, Object>>(){}.getType())`
  for parsing, and `gson.toJson(map)` for serialization. This is the exact
  pattern needed.
- Gson is maintained by Google, stable (no breaking API changes in years),
  and widely used (hundreds of thousands of dependents on Maven Central).

#### Tradeoffs

- Jackson is the industry standard for Java JSON and has a larger ecosystem
  (streaming API, tree model, annotation-based POJO mapping, data format
  modules). These features are not needed for Map-based serialization.
- If the API surface evolves toward typed response Records in a future
  version, Jackson would be the stronger choice. This can be revisited at
  that point. Swapping JSON libraries behind the transport layer is a
  localized change.

### API surface style

#### Key constraints

- pymqrest has ~200 command methods (`display_queue`, `define_qlocal`,
  `alter_queue`, etc.) organized as thin delegations to a central
  `_mqsc_command()` method. Each method sets the correct command/qualifier
  pair and calls through.
- MQ has hundreds of attributes per object type, and IBM adds new attributes
  across MQ versions. The attribute set is dynamic, not static.
- pymqrest supports strict mode (raise on unknown attributes) and permissive
  mode (pass unknown attributes through unchanged). Records cannot have
  unknown fields.

#### Evidence cited

- The method-per-command pattern maps directly to Java:
  `display_queue(name, request_parameters, response_parameters, where)` →
  `displayQueue(String name, Map<String, Object> requestParameters,
  List<String> responseParameters, String where)`.
- Return types mirror pymqrest: `List<Map<String, Object>>` for display
  commands returning multiple objects, `Map<String, Object>` (nullable or
  `Optional`) for singleton display commands, and `void` for action commands
  (define, alter, delete).
- The method-per-command approach is valid Java style. Libraries like JDBC
  (`executeQuery`, `executeUpdate`), Apache HttpClient (`execute`), and
  AWS SDK (`listBuckets`, `putObject`, `getObject`) all use
  method-per-operation patterns.

#### Tradeoffs

- `Map<String, Object>` loses compile-time type safety for attributes. The
  caller must know the correct attribute names and types. This is the same
  tradeoff pymqrest makes, and it is mitigated by the mapping layer (which
  translates snake_case names and provides error messages for unknown
  attributes in strict mode).
- A fluent builder API (`session.display().queue("MY.*").where(...)
  .execute()`) would provide better IDE discoverability but would require an
  enormous number of builder classes for 200+ commands with hundreds of
  attributes each. The maintenance burden would be disproportionate to the
  benefit.

#### Java 17+ features used

- **Records** for fixed-schema types that will not change with MQ versions:
  `TransportResponse(int statusCode, String body, Map<String, String>
  headers)`, `SyncConfig`, `SyncResult`, `EnsureResult`, `MappingIssue`,
  and credential types.
- **Sealed interfaces** for the `Credentials` type union:
  `sealed interface Credentials permits BasicAuth, LtpaAuth,
  CertificateAuth`. Enables exhaustive `switch` in Java 21+ and
  communicates the closed set of authentication methods.
- **Pattern matching for `instanceof`** in session credential handling
  (replacing Python's `isinstance` chains).

### Attribute mapping approach

#### Key constraints

- The mapping system is the most complex and error-prone subsystem in
  pymqrest. It has three layers (key map, value map, key-value map), two
  directions (request/response), strict/permissive modes, detailed error
  tracking (`MappingIssue`), and a sophisticated override mechanism with
  merge/replace modes.
- The mapping data (`mapping_data.py`, ~1900 lines) encodes the complete
  translation table between snake_case attribute names, MQSC parameter
  names, and their value transformations.
- Correctness of the mapping data is critical -- incorrect translations
  produce silent data corruption in MQ commands.

#### Evidence cited

- pymqrest's mapping data structure is a nested dictionary:

  ```python
  MAPPING_DATA = {
      "commands": {
          "DISPLAY QUEUE": {"qualifier": "queue", ...},
          ...
      },
      "qualifiers": {
          "queue": {
              "request_key_map": {"max_depth": "MAXDEPTH", ...},
              "response_key_map": {"MAXDEPTH": "max_depth", ...},
              "request_value_map": {...},
              "response_value_map": {...},
          },
          ...
      }
  }
  ```

  This translates directly to `Map<String, Object>` in Java.
- Storing the mapping data as a JSON resource file (`mapping_data.json` in
  `src/main/resources/`) provides several advantages over a Java static
  initializer:
  - Easy visual comparison with pymqrest's `mapping_data.py`
  - Inspectable by users without reading Java source
  - Compatible with the override mechanism (users provide a `Map` of the
    same shape)
  - Loaded once at class initialization via Gson (sub-millisecond for
    ~1900 lines)
- The mapping pipeline logic (key lookup, value transformation, strict mode
  error collection) is a pure function: input `Map<String, Object>`, output
  `Map<String, Object>`. This is trivially testable without mocking.

#### Tradeoffs

- A JSON resource file adds a runtime parse step at class loading time. The
  cost is negligible (~1ms for the mapping data) and happens once.
- The mapping data is not type-checked at compile time. Malformed JSON would
  produce a runtime error at class loading. This is mitigated by unit tests
  that load and validate the mapping data.
- An enum-based approach (`QueueAttribute.MAX_DEPTH`) would provide
  compile-time safety but would require a library release for every new MQ
  attribute and cannot handle permissive mode (unknown attributes).

### Exception hierarchy

#### Key constraints

- pymqrest defines six exception types under a base `MQRESTError`, plus a
  separate `MappingError` that does not extend `MQRESTError`.
- Java has both checked (`Exception`) and unchecked (`RuntimeException`)
  exceptions. This is the most consequential sub-decision.
- The exception types must carry contextual data (URL, status code, payload,
  mapping issues) for debugging.

#### Evidence cited

- The modern Java library convention is unchecked exceptions. Spring
  (`DataAccessException`), Hibernate (`HibernateException`), Jackson
  (`JsonProcessingException` -- technically checked but widely considered a
  design mistake), OkHttp (`IOException` wrappers), Gson
  (`JsonSyntaxException`), and most post-2010 libraries use unchecked
  exceptions.
- The rationale is well-established: checked exceptions force try/catch at
  every call site. For a library where the common case is "call a method,
  get a result," this creates boilerplate. Consider:

  ```java
  // Checked (every call site):
  try {
      var queues = session.displayQueue("MY.*");
  } catch (MqRestException e) { ... }

  // Unchecked (handle where appropriate):
  var queues = session.displayQueue("MY.*");
  ```

- pymqrest uses unchecked exceptions (Python has no checked exception
  concept). The Java port preserves this behavior.
- `sealed` on `MqRestException` documents the complete set of failure modes
  and prevents consumers from creating fragile subclasses:

  ```java
  public sealed class MqRestException extends RuntimeException
      permits MqRestTransportException, MqRestResponseException,
              MqRestAuthException, MqRestCommandException,
              MqRestTimeoutException
  ```

#### Exception hierarchy

```text
MqRestException (sealed, extends RuntimeException)
├── MqRestTransportException   (url)
├── MqRestResponseException    (responseText)
├── MqRestAuthException        (url, statusCode)
├── MqRestCommandException     (payload, statusCode)
└── MqRestTimeoutException     (name, operation, elapsed)

MappingException (extends RuntimeException, separate hierarchy)
    (issues: List<MappingIssue>)
```

- `MappingException` is separate from `MqRestException` (as in pymqrest).
  It is a data-transformation error, not a REST API error. It carries a
  `List<MappingIssue>` with detailed per-attribute error information.

#### Tradeoffs

- Unchecked exceptions do not force callers to handle errors. A caller who
  forgets to catch `MqRestCommandException` will get an unhandled exception
  at runtime. This is the standard tradeoff accepted by the Java ecosystem.
- Sealed classes prevent consumers from adding custom exception subtypes. If
  a consumer needs to distinguish additional failure modes, they must catch
  the base type and inspect fields. This is intentional -- the library
  controls its error hierarchy.

## Planned package structure

```text
io.github.wphillipmoore.mq.rest.admin
    MqRestSession, MqRestTransport (interface), HttpClientTransport,
    TransportResponse (record)

io.github.wphillipmoore.mq.rest.admin.auth
    Credentials (sealed interface), BasicAuth, LtpaAuth,
    CertificateAuth (records)

io.github.wphillipmoore.mq.rest.admin.exception
    MqRestException (sealed), MqRestTransportException,
    MqRestResponseException, MqRestAuthException,
    MqRestCommandException, MqRestTimeoutException

io.github.wphillipmoore.mq.rest.admin.mapping
    AttributeMapper, MappingData, MappingIssue (record),
    MappingException, MappingOverrideMode (enum)

io.github.wphillipmoore.mq.rest.admin.sync
    SyncConfig (record), SyncResult (record), SyncOperation (enum)

io.github.wphillipmoore.mq.rest.admin.ensure
    EnsureResult (record), EnsureAction (enum)
```

## Runtime dependency profile

| Dependency | Scope | Size | Transitive deps |
| --- | --- | --- | --- |
| `com.google.code.gson:gson` | compile | ~280KB | 0 |
| **Total** | | **~280KB** | **0** |

## Implementation sequencing (future PRs)

1. Exception hierarchy (no dependencies, needed by everything).
2. Transport interface + `TransportResponse` record (defines the HTTP
   boundary).
3. Auth types (`Credentials` sealed interface + record implementations).
4. `MappingIssue` record + `MappingException` (needed by mapping).
5. Mapping pipeline (`AttributeMapper` + `MappingData`, testable in
   isolation).
6. `MqRestSession` core (`_mqscCommand`, URL/header building, response
   parsing).
7. Command methods (thin delegations, high volume but simple).
8. Ensure methods (depend on command execution).
9. Sync methods (depend on command execution).
10. `HttpClientTransport` (actual JDK HTTP client implementation, deliberately
    last because it is behind the transport interface).

Steps 1-6 can proceed without a live MQ instance. Step 10 is deliberately
last because it is behind the transport interface and can be developed
independently.

## Options not chosen

### HTTP client: OkHttp

- **Description**: Well-designed HTTP client from Square, widely used in the
  Java and Android ecosystems.
- **Reason not chosen**: Pulls in Okio (~300KB) and kotlin-stdlib (~1.8MB) as
  transitive dependencies. For a library that makes one type of HTTP call
  (POST with JSON), this is unjustifiable dependency overhead. OkHttp's
  strengths (interceptor chains, connection pooling, HTTP/2 multiplexing) are
  irrelevant for this use case.
- **Status**: Rejected.

### HTTP client: Apache HttpClient 5

- **Description**: The established Java HTTP library, mature and
  feature-rich.
- **Reason not chosen**: Has 4+ transitive dependencies (httpcore5,
  slf4j-api, commons-codec, potentially others). Its API is more complex
  than needed. The classic Apache style (CloseableHttpClient, HttpPost,
  EntityUtils) is verbose for a simple POST.
- **Status**: Rejected.

### HTTP client: Retrofit

- **Description**: Declarative REST client built on OkHttp, annotation-driven
  endpoint definitions.
- **Reason not chosen**: Designed for APIs with multiple endpoints.
  mq-rest-admin has exactly one endpoint (`/admin/action/qmgr/{name}/mqsc`).
  Retrofit's annotation approach adds complexity with no benefit. Also
  inherits OkHttp's transitive dependency burden.
- **Status**: Rejected.

### JSON library: Jackson

- **Description**: The most popular Java JSON library, with extensive POJO
  mapping, streaming, and annotation support.
- **Reason not chosen**: Requires 3 JARs minimum (~2.1MB total). Its power
  (annotation-driven POJO mapping, modules, custom serializers) is unused
  when working with `Map<String, Object>`. `ObjectMapper` requires careful
  configuration (feature flags, module registration) that adds complexity
  for a first Java project.
- **Status**: Rejected. Can be revisited if the API surface evolves toward
  typed response Records.
- **Revisit trigger**: If the API surface moves from `Map<String, Object>` to
  typed return objects.

### JSON library: org.json (JSON-java)

- **Description**: Douglas Crockford's original Java JSON library.
- **Reason not chosen**: Has a non-standard license clause ("The Software
  shall be used for Good, not Evil") that creates legal uncertainty for
  consumers. Also lacks type token support for generic deserialization.
- **Status**: Rejected.

### JSON library: Jakarta JSON Processing (JSON-P)

- **Description**: The standard Java EE/Jakarta EE JSON API.
- **Reason not chosen**: Requires API + implementation JARs (e.g., Eclipse
  Parsson) with potential version conflicts for consumers who use different
  implementations. Overkill for this project.
- **Status**: Rejected.

### API surface: fluent builder

- **Description**: Type-safe, discoverable API like
  `session.display().queue("MY.*").where("currentDepth").greaterThan(100)
  .execute()`.
- **Reason not chosen**: Creates an enormous API surface (builder classes for
  each command type, qualifier, and comparison operator). For 200+ commands
  with hundreds of attributes, the maintenance burden is disproportionate.
  The primary goal is a Java port of pymqrest, not a reimagining.
- **Status**: Rejected.

### API surface: typed Record returns per qualifier

- **Description**: Define Java records like `QueueAttributes(String
  queueName, int currentDepth, ...)` for compile-time type safety.
- **Reason not chosen**: Couples the library to a specific MQ version's
  attribute set. Every new IBM attribute requires a library release. Cannot
  handle permissive mode (unknown attributes pass through). The mapping
  pipeline operates on Maps, not typed objects.
- **Status**: Rejected.

### API surface: generic command method only

- **Description**: Expose only `session.executeCommand("DISPLAY", "QUEUE",
  "MY.*", params)` without per-command convenience methods.
- **Reason not chosen**: Sacrifices discoverability and documentation. The
  per-command methods are the library's primary value proposition beyond raw
  HTTP calls. This generic method exists internally as `_mqscCommand` but
  should not be the only public API.
- **Status**: Rejected.

### Mapping: annotation-based mapping with Records

- **Description**: Use Jackson/Gson annotations on Record classes for
  attribute translation.
- **Reason not chosen**: Requires typed Record classes per qualifier (see
  "typed Record returns" above). Couples mapping to compile-time definitions,
  breaking the dynamic override system pymqrest provides.
- **Status**: Rejected.

### Mapping: Java enums for all attributes

- **Description**: Define enums like `QueueAttribute.MAX_DEPTH` with MQSC
  names as fields for type-safe attribute references.
- **Reason not chosen**: Every new MQ attribute requires a library release.
  Cannot handle permissive mode (unknown attributes). Would require a
  redesign of pymqrest's proven mapping pipeline.
- **Status**: Rejected. Can be revisited as an optional layer on top of the
  Map-based pipeline.

### Exceptions: checked (extends Exception)

- **Description**: Force callers to handle or declare all exceptions.
- **Reason not chosen**: Creates boilerplate at every call site. The modern
  Java library ecosystem has broadly moved away from checked exceptions.
  Spring, Hibernate, Jackson, OkHttp, Gson, and most post-2010 libraries
  use unchecked exceptions. pymqrest uses unchecked exceptions (Python has
  no checked exception concept).
- **Status**: Rejected.

### Exceptions: single exception class with error codes

- **Description**: One `MqRestException` with an error type enum instead of
  a class hierarchy.
- **Reason not chosen**: Loses the ability to catch specific failure types
  cleanly. The pymqrest hierarchy's specificity is valuable -- callers can
  catch `MqRestCommandException` without catching transport failures.
- **Status**: Rejected.

### Exceptions: non-sealed hierarchy

- **Description**: Allow consumers to extend the exception hierarchy with
  custom subtypes.
- **Reason not chosen**: No clear benefit, and creates risk of consumers
  creating fragile subclasses that depend on internal library behavior.
  Sealed is safer and more informative.
- **Status**: Rejected.

## Dependencies and external constraints

- Gson 2.x has been API-stable for years. It supports Java 17+ without
  issues. The latest version at time of decision is 2.12.1.
- `java.net.http.HttpClient` is part of the `java.net.http` module, which
  has been in the JDK since Java 11 and is fully stable.
- The transport interface design (`MqRestTransport`) ensures the HTTP client
  choice can be changed in the future without affecting the public API.
  Library consumers interact with `MqRestSession`, not `HttpClientTransport`.
- The Gson dependency can be swapped for Jackson in the future if the API
  surface evolves toward typed Records. The JSON library is used behind the
  transport and mapping layers, not exposed in the public API.

## References

- `docs/plans/2026-02-12-tier1-decisions.md` -- tier 1 decisions
- `docs/plans/2026-02-12-tier2-decisions.md` -- tier 2 decisions
- `docs/plans/open-decisions.md` -- remaining open decisions
- `docs/research/admin-rest-api-gap-analysis.md` -- REST API gap analysis
- `docs/research/mq-java-ecosystem.md` -- ecosystem survey
- `../pymqrest` -- reference implementation
- `../pymqrest/src/pymqrest/session.py` -- transport protocol and session
  logic
- `../pymqrest/src/pymqrest/exceptions.py` -- exception hierarchy
- `../pymqrest/src/pymqrest/mapping.py` -- mapping pipeline
- `../pymqrest/src/pymqrest/mapping_data.py` -- mapping data
- `../standards-and-conventions/docs/foundation/summarize-decisions-protocol
  .md` -- protocol followed for this document
