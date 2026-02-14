# API Reference

## Table of Contents

- [Core](#core)
- [Authentication](#authentication)
- [Mapping](#mapping)
- [Exceptions](#exceptions)
- [Patterns](#patterns)


This section documents the public API of mq-rest-admin. For generated Javadoc,
see the [Javadoc](../javadoc.md) page.

## Core

- [Session](session.md) — `MqRestSession` main entry point
- [Commands](commands.md) — MQSC command methods
- [Transport](transport.md) — `MqRestTransport` and `HttpClientTransport`

## Authentication

- [Auth](auth.md) — `Credentials` sealed interface and implementations

## Mapping

- [Mapping](mapping.md) — `AttributeMapper`, `MappingData`, `MappingOverrideMode`

## Exceptions

- [Exceptions](exceptions.md) — `MqRestException` hierarchy

## Patterns

- [Ensure](ensure.md) — `EnsureResult`, `EnsureAction`
- [Sync](sync.md) — `SyncConfig`, `SyncResult`, `SyncOperation`
