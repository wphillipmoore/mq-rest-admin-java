# IBM MQ Java ecosystem survey

## Table of Contents

- [Purpose](#purpose)
- [Official IBM artifacts on Maven Central](#official-ibm-artifacts-on-maven-central)
- [What each library provides](#what-each-library-provides)
- [PCF: the closest thing to admin in Java](#pcf-the-closest-thing-to-admin-in-java)
- [Third-party libraries](#third-party-libraries)
- [IBM GitHub repositories and naming conventions](#ibm-github-repositories-and-naming-conventions)
- [Published package names across languages](#published-package-names-across-languages)
- [Community naming conventions](#community-naming-conventions)
- [Conclusion](#conclusion)

## Purpose

Document the existing IBM MQ Java library ecosystem to inform dependency
decisions and project naming for the Java port of pymqrest. Research conducted
2026-02-12.

## Official IBM artifacts on Maven Central

All official IBM MQ Java artifacts are published under groupId `com.ibm.mq`.

| Artifact ID | Latest Version | Purpose |
| --- | --- | --- |
| `com.ibm.mq.allclient` | 9.4.3.0 | Uber-JAR: MQ Java + JMS + PCF + Headers |
| `com.ibm.mq.jakarta.client` | 9.4.3.0 | Jakarta Messaging 3.0 equivalent of allclient |
| `mq-jms-spring-boot-starter` | 3.5.3 | Spring Boot autoconfiguration for JMS |
| `mq-jms-spring-testcontainer` | 3.5.3 | Spring test support with Testcontainers |
| `mq-java-testcontainer` | 1.21.2 | Testcontainers integration for MQ |
| `wmq.jmsra` | 9.4.3.0 | JMS Resource Adapter (javax) |
| `wmq.jmsra.ivt` | 9.4.3.0 | JMS RA Installation Verification Test |
| `wmq.jakarta.jmsra` | 9.4.3.0 | JMS Resource Adapter (jakarta) |
| `wmq.jakarta.jmsra.ivt` | 9.4.3.0 | Jakarta JMS RA IVT |

There are no separate `com.ibm.mq:com.ibm.mq`, `com.ibm.mq:com.ibm.mq.pcf`,
or `com.ibm.mq:com.ibm.mq.headers` artifacts on Maven Central. These exist
only as internal packages within the uber-JARs or as legacy JARs in the MQ
installation directory (not published to Maven).

## What each library provides

### com.ibm.mq.allclient (the uber-JAR)

Messaging and low-level administration via PCF. Contains:

- IBM MQ classes for Java (`MQQueueManager`, `MQQueue`, `MQMessage`)
- IBM MQ classes for JMS 2.0 (`MQConnectionFactory`)
- PCF classes (`PCFMessageAgent`, `PCFMessage`)
- Headers classes (`MQHeaderList`, `MQRFH2`, `MQCIH`)

**Transport**: Native MQ wire protocol over TCP (default port 1414) or JNI local
bindings. Does NOT use HTTP/REST. Has no awareness of the mqweb server, port
9443, or any REST endpoints.

### com.ibm.mq.jakarta.client

Identical in function to allclient, except uses `jakarta.jms.*` package names
instead of `javax.jms.*`. Supports Jakarta Messaging 3.0. Contains the same
PCF and Headers packages. Same transport (native MQ protocol, not REST).

### mq-jms-spring-boot-starter

Spring Boot autoconfiguration for JMS messaging with IBM MQ. Source:
[ibm-messaging/mq-jms-spring](https://github.com/ibm-messaging/mq-jms-spring).
Auto-creates `ConnectionFactory`, `JmsTemplate`, and `MessageListener` beans
from `ibm.mq.*` properties. Purely JMS messaging autoconfiguration -- no
administration capabilities.

### com.ibm.mq.headers (package in allclient)

Helper classes for constructing and parsing MQ message headers (`MQRFH2`,
`MQCIH`, `MQDLH`, `MQXQH`, etc.). Data-format helpers, not a transport layer.

## PCF: the closest thing to admin in Java

The `com.ibm.mq.pcf` package (bundled inside `allclient.jar`) provides
Programmable Command Format support for administration.

### How PCF works

1. `PCFMessageAgent` opens a connection to the queue manager (TCP port 1414 or
   JNI bindings).
2. It constructs a binary PCF message (`MQCFH` header + parameter structures).
3. It puts the message onto `SYSTEM.ADMIN.COMMAND.QUEUE`.
4. The command server on the queue manager reads it, executes the command, and
   replies.
5. `PCFMessageAgent` reads the reply.

### PCF vs the REST API

| Aspect | PCF | REST API |
| --- | --- | --- |
| Transport | MQ messages via TCP:1414 / JNI | HTTP/HTTPS via mqweb (TCP:9443) |
| Wire format | Binary PCF structures | JSON over HTTP |
| Requires | MQ client libraries (native code) | Any HTTP client |
| Prerequisite | MQ listener + command server | mqweb server (embedded Liberty) |
| Arbitrary MQSC | Yes, via Escape PCF (`MQCMD_ESCAPE`) | Yes, via `runCommand` / `runCommandJSON` |
| Response format | Binary PCF (typed) or text (escape) | Structured JSON or text |
| Age | MQ V5 era (decades old) | MQ 9.0.1 (2017) |

PCF and the admin REST API are completely independent mechanisms. The mqweb
server likely translates REST requests into PCF internally, but this is opaque.

### Can PCF execute arbitrary MQSC?

Yes, through Escape PCF (`MQCMD_ESCAPE` with `EscapeType` set to `MQET_MQSC`).
The MQSC text goes in the `EscapeText` parameter. Responses come back as
unparsed text strings, making programmatic consumption difficult. This is how
`runmqsc` in client mode works internally.

## Third-party libraries

### fbraem/mqweb

A standalone C++ HTTP server (built on the POCO framework) that exposes its own
REST API for querying MQ objects. Connects via the native MQI C API, not through
IBM's REST API. Endpoints like `/api/queue/inquire` translate to PCF commands
internally. Predates IBM's own REST API. Not relevant as a dependency (C++, own
API surface).

### IBM MQ MCP Server

IBM's own Model Context Protocol server for LLM integration
([ibm-messaging/mq-mcp-server](https://github.com/ibm-messaging/mq-mcp-server)).
Written in Python, calls the admin REST API using raw `httpx` HTTP calls.
Confirms that IBM themselves have no SDK for the REST API.

### Other

No Java library on Maven Central, GitHub, or elsewhere wraps the IBM MQ
administrative REST API.

## IBM GitHub repositories and naming conventions

The [ibm-messaging](https://github.com/ibm-messaging) organization (121+
repositories) uses consistent naming: `mq-{purpose}` or
`mq-{protocol}-{language}`.

| Repository | Language | Purpose |
| --- | --- | --- |
| `mq-jms-spring` | Java | Spring Boot JMS integration |
| `mq-golang` | Go | Go MQI wrapper |
| `mq-golang-jms20` | Go | JMS 2.0 style interface for Go |
| `mq-mqi-nodejs` | JS | Node.js MQI wrapper |
| `mq-mqi-python` | Python | Python MQI wrapper (`ibmmq` package) |
| `mq-metric-samples` | Go | Prometheus/CloudWatch metric exporters |
| `mq-container` | Docker | Container images |
| `mq-ansible` | Ansible | Ansible roles |
| `mq-helm` | Helm | Kubernetes Helm charts |
| `mq-sample-web-ui` | JS | Sample web UI using admin REST API |
| `mq-dotnet-administration-with-mqrestapi` | .NET | .NET samples for admin REST API |

For MQI wrappers, the pattern is `mq-mqi-{language}`. For other libraries, the
pattern is `mq-{technology}-{purpose}`.

## Published package names across languages

| Language | Registry | Package Name | GitHub Repo |
| --- | --- | --- | --- |
| Java | Maven Central | `com.ibm.mq:com.ibm.mq.allclient` | (closed source) |
| Node.js | npm | `ibmmq` | `ibm-messaging/mq-mqi-nodejs` |
| Python | PyPI | `ibmmq` | `ibm-messaging/mq-mqi-python` |
| Go | Go modules | `github.com/ibm-messaging/mq-golang/v5/ibmmq` | `ibm-messaging/mq-golang` |

IBM converged on `ibmmq` (all lowercase, no separators) as the published package
name across npm, PyPI, and Go.

## Community naming conventions

| Project | Convention | Example |
| --- | --- | --- |
| pymqi (Python) | `py` + protocol | Python + MQI = `pymqi` |
| pymqrest (Python) | `py` + product + interface | Python + MQ + REST = `pymqrest` |
| mqweb (C++) | product + interface | MQ + web = `mqweb` |
| Third-party Java | hyphenated, includes `mq` or `ibm-mq` | `mq-java-exporter` |

## Conclusion

Every IBM MQ Java artifact on Maven Central is a messaging client that uses the
native MQ wire protocol (TCP port 1414 or JNI bindings). None support the
administrative REST API. IBM does not publish a Java SDK for the admin REST API.
The gap this project fills is genuine and there is no existing library to extend
or wrap.
