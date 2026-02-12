# IBM MQ administrative REST API gap analysis

## Table of Contents

- [Purpose](#purpose)
- [The administrative REST API](#the-administrative-rest-api)
- [REST API endpoints](#rest-api-endpoints)
- [The MQSC endpoint in detail](#the-mqsc-endpoint-in-detail)
- [Authentication](#authentication)
- [REST API version history](#rest-api-version-history)
- [Existing client libraries for the admin REST API](#existing-client-libraries-for-the-admin-rest-api)
- [Dependency implications](#dependency-implications)
- [Scope considerations](#scope-considerations)

## Purpose

Analyze the IBM MQ administrative REST API surface, confirm that no existing
Java client library covers it, and document the implications for project
dependencies and scope. Research conducted 2026-02-12.

## The administrative REST API

The REST API is served by the **mqweb server** (an embedded Liberty server),
typically on port 9443 (HTTPS). Base URL pattern:

```text
https://{host}:{port}/ibmmq/rest/v{version}/
```

This is completely independent from the native MQ wire protocol (port 1414) used
by `com.ibm.mq.allclient` and PCF.

## REST API endpoints

### Administrative endpoints

| Endpoint | Methods | Purpose |
|---|---|---|
| `/admin/action/qmgr/{qmgrName}/mqsc` | POST | Execute MQSC commands |
| `/admin/installation` | GET | Query MQ installations |
| `/admin/qmgr` | GET | List queue managers and status |
| `/admin/qmgr/{qmgrName}/queue` | GET, POST, PATCH, DELETE | CRUD on queues |
| `/admin/qmgr/{qmgrName}/channel` | GET | Query channels |
| `/admin/qmgr/{qmgrName}/subscription` | GET | Query subscriptions |
| `/admin/mft/agent` | GET | Query MFT agents |
| `/admin/mft/call` | GET, POST | Manage MFT calls |
| `/admin/mft/monitor` | GET, POST | Manage MFT monitors |
| `/admin/mft/transfer` | GET, POST | Manage MFT transfers |
| `/login` | POST, DELETE | Authentication (LTPA token) |

### Messaging endpoints (separate from admin)

| Endpoint | Methods | Purpose |
|---|---|---|
| `/messaging/qmgr/{qmgrName}/queue/{qName}/message` | POST, DELETE | Send/receive messages |
| `/messaging/qmgr/{qmgrName}/topic/{topicString}/message` | POST | Publish to topic |

## The MQSC endpoint in detail

**URL**: `POST /ibmmq/rest/v2/admin/action/qmgr/{qmgrName}/mqsc`

Two modes are available on this single endpoint:

### runCommand (plain text MQSC)

```json
{
  "type": "runCommand",
  "parameters": {
    "command": "DISPLAY QLOCAL(MY.QUEUE) ALL"
  }
}
```

Response contains text strings in `commandResponse[].text[]`.

### runCommandJSON (structured JSON MQSC)

```json
{
  "type": "runCommandJSON",
  "command": "display",
  "qualifier": "qlocal",
  "name": "MY.QUEUE",
  "responseParameters": ["all"]
}
```

Response contains structured JSON (easier to parse programmatically). This is
the mode that pymqrest uses exclusively.

### Required HTTP headers

- `Content-Type: application/json`
- `ibm-mq-rest-csrf-token: <any value>` (CSRF protection; value can be blank)
- `Authorization: Basic <base64>` (or LTPA token cookie from `/login`)

## Authentication

Two mechanisms are supported:

1. **Basic authentication**: Standard HTTP Basic auth header. User must be in
   `MQWebAdmin`, `MQWebAdminRO`, or `MQWebUser` role.
2. **LTPA token**: POST to `/login` to obtain an LTPA cookie, then include the
   cookie in subsequent requests.

pymqrest currently supports basic auth and CSRF token handling.

## REST API version history

| Version | Introduced In | Notes |
|---|---|---|
| v1 | IBM MQ 9.0.1 | Initial REST API |
| v2 | IBM MQ 9.1.5 | Additional features |
| v3 | IBM MQ 9.3.0 | Current version |

The `/admin/action/qmgr/{qmgrName}/mqsc` endpoint has been available since at
least v1. IBM provides an OpenAPI/Swagger spec at the
`/rest/v1/openapi.json` endpoint on the mqweb server.

## Existing client libraries for the admin REST API

### Java

**None.** No Java library on Maven Central, GitHub, or elsewhere wraps the IBM
MQ administrative REST API.

### Other languages

| Project | Language | Approach |
|---|---|---|
| pymqrest | Python | Full client library (this project's reference implementation) |
| mq-dotnet-administration-with-mqrestapi | C# / .NET | IBM sample code, raw HTTP calls |
| mq-sample-web-ui | JavaScript | IBM sample web page, raw fetch calls |
| mq-mcp-server | Python | IBM MCP server, raw httpx calls |

IBM's own projects (MCP server, .NET sample) use raw HTTP calls, confirming
there is no official SDK in any language.

## Dependency implications

This project does **not** need `com.ibm.mq.allclient` or any other IBM MQ
client library. The admin REST API is standard HTTP/JSON. Required dependencies
are:

1. **HTTP client**: `java.net.http.HttpClient` (built into JDK 11+), OkHttp, or
   Apache HttpClient.
2. **JSON library**: Jackson, Gson, or similar.

No native code, JNI bindings, or MQ-specific transport libraries are required.

## Scope considerations

The admin REST API offers two levels of functionality:

### Level 1: MQSC endpoint (pymqrest parity)

The `/admin/action/qmgr/{qmgrName}/mqsc` endpoint with `runCommandJSON` mode.
This is what pymqrest wraps today and provides access to the full MQSC command
set.

### Level 2: Resource-specific CRUD endpoints

Dedicated endpoints for queues, channels, subscriptions, and queue manager info.
These accept direct HTTP verbs (GET, POST, PATCH, DELETE) against resource URLs
and do not require constructing MQSC command text.

Whether the Java port covers only level 1 (MQSC endpoint, pymqrest parity) or
also level 2 (resource CRUD endpoints) is an open design decision. Level 2
endpoints could offer a more idiomatic Java API surface for common operations.
