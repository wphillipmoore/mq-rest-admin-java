# mq-rest-admin

## Overview

**mq-rest-admin** provides a Java mapping layer for MQ REST API attribute
translations and command metadata. It wraps the complexity of the
`runCommandJSON` endpoint behind typed Java methods that map 1:1 to MQSC
commands, translate attribute names between developer-friendly `snake_case` and
native MQSC tokens, and surface errors as structured exceptions.

## Key features

- **~144 command methods** covering all MQSC verbs and qualifiers
- **Bidirectional attribute mapping** between developer-friendly names and MQSC parameters
- **Idempotent ensure methods** for declarative object management
- **Bulk sync operations** for configuration-as-code workflows
- **Zero runtime dependencies** beyond Gson (~280KB)
- **Transport abstraction** for easy testing with mock transports

## Build coordinates

```xml
<dependency>
    <groupId>io.github.wphillipmoore</groupId>
    <artifactId>mq-rest-admin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Status

This project is in **pre-alpha** (initial setup). The API surface, mapping
tables, and return shapes are under active development.

## License

GNU General Public License v3.0
