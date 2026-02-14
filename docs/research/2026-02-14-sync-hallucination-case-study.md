# Case Study: AI Documentation Hallucination in the Sync Package

## Table of Contents

- [Summary](#summary)
- [Background](#background)
- [What Python Actually Implements](#what-python-actually-implements)
- [What the Java Code Implements](#what-the-java-code-implements)
- [What the Java Documentation Claims](#what-the-java-documentation-claims)
- [What the Go Port Implements](#what-the-go-port-implements)
- [Forensic Timeline](#forensic-timeline)
- [Root Cause Analysis](#root-cause-analysis)
  - [The semantic ambiguity of "sync"](#the-semantic-ambiguity-of-sync)
  - [How the hallucination was constructed](#how-the-hallucination-was-constructed)
  - [Why the code agent got it right](#why-the-code-agent-got-it-right)
  - [Why the doc agent got it wrong](#why-the-doc-agent-got-it-wrong)
  - [The fragment-first architecture amplified the error](#the-fragment-first-architecture-amplified-the-error)
- [The Hallucinated Design in Detail](#the-hallucinated-design-in-detail)
- [Contamination Radius](#contamination-radius)
- [Why It Went Undetected](#why-it-went-undetected)
- [Lessons Learned](#lessons-learned)
- [References](#references)

## Summary

During the Java documentation site build on 2026-02-14, the AI agent tasked
with writing documentation for the sync package invented a completely fictional
API instead of documenting the actual implementation. The fabricated concept --
a Terraform-style bulk declarative configuration sync system -- has no basis in
the reference implementation (Python), the Go port, or the Java source code
itself. The Java code was implemented correctly; only the documentation is
wrong.

This case study traces the forensic timeline, identifies the root cause, and
documents lessons learned for AI-assisted documentation workflows.

## Background

The mq-rest-admin library family (Python, Java, Go) provides a wrapper for the
IBM MQ administrative REST API. Each implementation is a port of the original
Python version (`pymqrest`), aiming for feature and API parity.

The sync package provides **synchronous wrappers** around normally
fire-and-forget MQSC START and STOP commands. When you issue `START CHANNEL`,
MQ accepts the command and returns immediately -- the channel may still be
starting. The sync methods add a polling loop: issue the command, then
repeatedly DISPLAY the object's status until it reaches the target state (or
timeout).

The word "sync" here means **synchronous** (wait for completion), not
**synchronize** (reconcile state).

## What Python Actually Implements

Source: `mq-rest-admin-python/src/pymqrest/sync.py` (454 lines)

Nine methods across three object types:

| Object | Start | Stop | Restart |
| --- | --- | --- | --- |
| Channel | `start_channel_sync()` | `stop_channel_sync()` | `restart_channel()` |
| Listener | `start_listener_sync()` | `stop_listener_sync()` | `restart_listener()` |
| Service | `start_service_sync()` | `stop_service_sync()` | `restart_service()` |

Supporting types:

- **`SyncConfig`**: `timeout_seconds` (default 30), `poll_interval_seconds`
  (default 1)
- **`SyncOperation`**: Enum with values `STARTED`, `STOPPED`, `RESTARTED`
- **`SyncResult`**: `operation`, `polls` (count), `elapsed_seconds`
- **`MQRESTTimeoutError`**: Raised when polling exceeds timeout

Polling logic:

1. Issue START or STOP command
2. Sleep for `poll_interval_seconds`
3. Issue DISPLAY on the status qualifier (CHSTATUS, LSSTATUS, or SVSTATUS)
4. Check if status matches target (RUNNING or STOPPED)
5. Special case: channels disappear from CHSTATUS when stopped, so empty
   response means stopped
6. Return `SyncResult` or raise `MQRESTTimeoutError`

Restart is stop-then-start, combining metrics from both phases.

## What the Java Code Implements

Source: `MqRestSession.java` (sync methods at lines 2272-2485), plus
`sync/SyncConfig.java`, `sync/SyncOperation.java`, `sync/SyncResult.java`

The Java code is a **correct port** of the Python implementation:

- Same 9 public methods: `startChannelSync()`, `stopChannelSync()`,
  `restartChannel()`, etc.
- Same `SyncConfig` record: `timeoutSeconds`, `pollIntervalSeconds`
- Same `SyncOperation` enum: `STARTED`, `STOPPED`, `RESTARTED`
- Same `SyncResult` record: `operation`, `polls`, `elapsedSeconds`
- Same polling logic with `ObjectTypeConfig`, `hasStatus()` helper, and
  `Clock` abstraction for testability
- Same channel-specific behavior (empty CHSTATUS = stopped)
- 910 lines of unit tests, 100% line and branch coverage

Commit `48bb4e9` (2026-02-13 15:13): "Add synchronous start/stop/restart
methods with polling for channels, listeners, and services."

The code agent read the Python source, understood the pattern, and ported it
correctly.

## What the Java Documentation Claims

Source: `docs/site/docs/sync-methods.md`, `docs/site/docs/api/sync.md`

The documentation describes a **completely different system** that does not
exist:

```java
// HALLUCINATED CODE — this API does not exist
var desiredQueues = List.of(
    Map.of("name", "APP.REQUEST", "max_depth", 10000),
    Map.of("name", "APP.REPLY", "max_depth", 5000),
    Map.of("name", "APP.DLQ", "max_depth", 50000)
);

SyncConfig config = SyncConfig.builder()
    .qualifier("QLOCAL")
    .desiredState(desiredQueues)
    .deleteExtras(false)
    .nameFilter("APP.*")
    .build();

SyncResult result = session.sync(config);

System.out.println("Created: " + result.created());
System.out.println("Altered: " + result.altered());
System.out.println("Unchanged: " + result.unchanged());
System.out.println("Deleted: " + result.deleted());
```

The documented API describes a Terraform-style declarative state reconciliation
system: declare what objects should exist with what attributes, and the library
compares against the queue manager's current state, creating, altering, or
deleting objects to converge.

Side-by-side comparison of documented vs actual:

| Aspect | Documentation claims | Actual implementation |
| --- | --- | --- |
| `SyncConfig` fields | `qualifier`, `desiredState`, `deleteExtras`, `nameFilter` | `timeoutSeconds`, `pollIntervalSeconds` |
| `SyncConfig` creation | Builder pattern: `SyncConfig.builder()...build()` | Record constructor: `new SyncConfig(30.0, 1.0)` |
| Entry point | `session.sync(config)` | `session.startChannelSync(name, config)` (9 methods) |
| `SyncResult` fields | `created`, `altered`, `unchanged`, `deleted`, `operations` | `operation`, `polls`, `elapsedSeconds` |
| `SyncOperation` | Record: `name`, `action`, `attributes` | Enum: `STARTED`, `STOPPED`, `RESTARTED` |
| Concept | Bulk declarative state reconciliation | Synchronous polling for start/stop completion |

Every element of the documented API is fabricated. The type names (`SyncConfig`,
`SyncResult`, `SyncOperation`) are reused from the actual implementation, but
their fields, semantics, and usage are entirely different.

## What the Go Port Implements

Source: `mq-rest-admin-go/mqrestadmin/session_sync.go`, `sync.go`

The Go port also implements the **correct** Python pattern:

- Same 9 methods: `StartChannelSync()`, `StopChannelSync()`,
  `RestartChannel()`, etc.
- Same `SyncConfig`: `Timeout`, `PollInterval` (as `time.Duration`)
- Same `SyncOperation`: `SyncStarted`, `SyncStopped`, `SyncRestarted`
- Same `SyncResult`: `Operation`, `Polls`, `ElapsedSeconds`
- Same polling logic, same channel stop edge case
- `TimeoutError` for polling timeout

The Go agent was given essentially the same instructions ("port Python to Go")
and got it right.

## Forensic Timeline

All times are 2026-02-14 unless noted.

| Time | Commit | Event | Correct? |
| --- | --- | --- | --- |
| Feb 13 15:13 | `48bb4e9` | Java sync **code** created (PR #18) | **Yes** |
| Feb 14 07:23 | `df517ab` | Sync tests updated for NullAway (PR #30) | N/A |
| Feb 14 13:20 | `247bf94` | Common repo created; `sync-pattern.md` fragment written | **No** |
| Feb 14 13:38 | `a27f8ab` | Java docs site created (PR #41); sync docs written | **No** |
| Feb 14 14:31 | `54b44ab` | Docs enriched (PR #47); sync docs left untouched | N/A |
| Feb 14 | | Issue #44 filed: "sync package design is wrong" | |

The hallucination originated at 13:20 in the common fragment
`concepts/sync-pattern.md`, then propagated to the Java docs 18 minutes later
at 13:38. The code had been correct for 22 hours before the docs were written.

## Root Cause Analysis

### The semantic ambiguity of "sync"

The word "sync" has two plausible meanings in this context:

- **Meaning A (correct)**: Short for "synchronous." Make fire-and-forget
  commands wait for completion via polling. This is what the Python, Go, and
  Java code all implement.
- **Meaning B (hallucinated)**: Short for "synchronize." Reconcile desired
  state against actual state, like Terraform, Ansible, or Kubernetes. This is
  what the documentation describes.

Both meanings are common in software engineering. Without reading the actual
implementation, an AI agent could plausibly infer either meaning.

### How the hallucination was constructed

The fabricated sync concept was not random. It was systematically assembled from
real elements in the codebase:

| Hallucinated element | Actual source | Transformation |
| --- | --- | --- |
| "Declarative state management" | Python `ensure-methods.md`: "The `ensure_*()` methods implement a declarative upsert pattern" | Scaled from single-object to multi-object |
| "Created / Altered / Unchanged" | Python `EnsureAction` enum values | Repackaged as `SyncResult` counters |
| "Delete extras" | No source — invented | Novel addition to the scaled-up ensure concept |
| "Name filter" | MQSC DISPLAY commands support `*` wildcards | Repurposed as a scope limiter for delete safety |
| "Desired state list" | Python `ensure-methods.md`: "designed for scripts that declare desired state" | Applied to the sync concept instead of ensure |
| "Object type qualifier" | Python command dispatch (`_mqsc_command`) | Used as a sync target selector |

The agent effectively took the ensure pattern (single-object declarative
upsert), imagined "what would it look like if ensure worked on a list of objects
instead of just one?", added a delete option for convergence, and called it
"sync." The result is a coherent, internally consistent design. It just has
nothing to do with what "sync" actually means in this library.

A search for `desired_state`, `delete_extras`, `bulk sync`, or `declarative
sync` across the Python and Go codebases returns **zero results**. The only
"desired state" language in the entire Python codebase appears in
`ensure-methods.md`, confirming that the concept was borrowed from the wrong
feature.

### Why the code agent got it right

The code agent (commit `48bb4e9`, Feb 13) had direct access to:

- The Python source code: `sync.py` (454 lines of unambiguous polling logic)
- The Python tests: `test_sync.py` (35+ test cases showing exact behavior)
- The actual type definitions with their fields

When porting code, the agent reads the implementation. The Python
`start_channel_sync()` method contains a `while` loop calling `time.sleep()`
and `DISPLAY CHSTATUS`. There is no ambiguity about what "sync" means when you
are reading a polling loop.

### Why the doc agent got it wrong

The doc agent (commits `247bf94` and `a27f8ab`, Feb 14) was working from a
different information set:

- **The plan file** listed sync as a 3-word line item: `sync-methods.md` with
  the comment "Java sync examples", and `api/sync.md` with the comment
  "SyncConfig, SyncResult"
- **CLAUDE.md** listed type names without explaining their fields:
  `SyncConfig, SyncResult, SyncOperation`
- **The extraction table** in the plan said: extract `concepts/sync-pattern.md`
  from Python's `sync-methods.md` (conceptual parts)

The plan told the agent to extract conceptual content from the Python sync docs
into a shared fragment. But the agent did not actually read the Python
`sync-methods.md`. Instead, it inferred the concept from:

1. The word "sync" (ambiguous)
2. The nearby ensure pattern (which **is** about declarative state management)
3. The plan's mention of "Bulk sync operations" in the project overview

The agent constructed a plausible interpretation, wrote a coherent fragment, and
then wrote Java docs consistent with that fragment. The hallucination is
internally consistent across the fragment, the conceptual page, and the API
reference -- which made it harder to detect.

### The fragment-first architecture amplified the error

The documentation architecture requires writing a language-neutral shared
fragment first, then wrapping it with language-specific content. This design,
while sound for correct content, created a two-step amplification chain for the
hallucination:

1. **Step 1**: Write `concepts/sync-pattern.md` in mq-rest-admin-common
   (13:20). This fragment describes the wrong concept but appears authoritative
   because it's in the "shared source of truth" repo.
2. **Step 2**: Write Java docs that include the fragment via `--8<--` (13:38).
   The Java page now builds on the fragment's wrong concept, adding fabricated
   Java code examples that are consistent with the fragment.

If the agent had been writing a standalone page, the absence of any reference
implementation to copy from might have prompted it to read the actual source
code. But the fragment-first pattern gave the agent a "source" to work from --
one it had just written itself 18 minutes earlier.

## The Hallucinated Design in Detail

For the record, here is exactly what the documentation describes. None of this
exists.

### Hallucinated `sync-pattern.md` fragment

```text
The sync pattern provides bulk declarative configuration management. Given a
list of desired object definitions, sync compares them against the current state
of the queue manager and applies the minimum set of changes to reach the desired
state.

Operations:
- Create: Object does not exist on the queue manager — issue DEFINE.
- Alter: Object exists but attributes differ — issue ALTER.
- Unchanged: Object already matches — no action needed.

Objects that exist on the queue manager but are not in the desired state list
can optionally be deleted (controlled by a configuration flag).
```

### Hallucinated `SyncConfig`

```java
SyncConfig config = SyncConfig.builder()
    .qualifier("QLOCAL")
    .desiredState(desiredQueues)
    .deleteExtras(false)
    .nameFilter("APP.*")
    .build();
```

The actual `SyncConfig` is a Java record with two fields:

```java
public record SyncConfig(double timeoutSeconds, double pollIntervalSeconds) { }
```

### Hallucinated `SyncResult`

```java
public record SyncResult(
    int created,
    int altered,
    int unchanged,
    int deleted,
    List<SyncOperation> operations
) {}
```

The actual `SyncResult` is:

```java
public record SyncResult(SyncOperation operation, int polls, double elapsedSeconds) { }
```

### Hallucinated `SyncOperation`

```java
public record SyncOperation(
    String name,
    SyncAction action,
    Map<String, Object> attributes
) {}
```

The actual `SyncOperation` is a three-value enum:

```java
public enum SyncOperation { STARTED, STOPPED, RESTARTED }
```

## Contamination Radius

### Files containing the hallucinated concept

| File | Repo | Status |
| --- | --- | --- |
| `fragments/concepts/sync-pattern.md` | mq-rest-admin-common | Origin of hallucination |
| `docs/site/docs/sync-methods.md` | mq-rest-admin-java | Includes fragment + fabricated Java examples |
| `docs/site/docs/api/sync.md` | mq-rest-admin-java | Fabricated API reference |

### Files that are correct about sync

| File | Repo | Status |
| --- | --- | --- |
| `src/main/java/.../sync/SyncConfig.java` | mq-rest-admin-java | Correct (matches Python) |
| `src/main/java/.../sync/SyncOperation.java` | mq-rest-admin-java | Correct (matches Python) |
| `src/main/java/.../sync/SyncResult.java` | mq-rest-admin-java | Correct (matches Python) |
| `MqRestSession.java` (sync methods) | mq-rest-admin-java | Correct (matches Python) |
| `MqRestSessionSyncTest.java` | mq-rest-admin-java | Correct (910 lines, 100% coverage) |
| `sync/SyncConfigTest.java` | mq-rest-admin-java | Correct |
| `sync/SyncOperationTest.java` | mq-rest-admin-java | Correct |
| `sync/SyncResultTest.java` | mq-rest-admin-java | Correct |
| All Go sync files | mq-rest-admin-go | Correct (matches Python) |

### Files with minor contamination

| File | Repo | Issue |
| --- | --- | --- |
| `CLAUDE.md` | mq-rest-admin-java | Lists `SyncConfig, SyncResult, SyncOperation` without wrong descriptions; technically not wrong but provides no context |

## Why It Went Undetected

Several factors allowed the hallucination to survive through PR review:

1. **Internal consistency**: The fragment, the conceptual page, and the API
   reference all agree with each other. A reviewer reading the docs top-down
   would find a coherent story.

2. **Plausible design**: "Bulk declarative sync" is a well-known pattern
   (Terraform, Ansible, Kubernetes controllers). The design is sensible. It
   just isn't what this library does.

3. **Correct type names**: The hallucinated docs reuse the actual type names
   (`SyncConfig`, `SyncResult`, `SyncOperation`) -- just with completely
   different fields and semantics. A quick scan of the docs wouldn't reveal the
   mismatch without also reading the source code.

4. **Doc enrichment left it untouched**: During the PR #47 enrichment pass, the
   agent was explicitly instructed to leave sync docs untouched (the user had
   already identified the problem). This meant the hallucination survived a
   second review pass without being flagged as wrong by the agent.

5. **No build-time validation**: MkDocs with `strict: true` validates links and
   structure but cannot verify that code examples match actual API surfaces.
   The hallucinated code compiles as valid Java syntax -- it just references
   methods and fields that don't exist.

## Lessons Learned

### For AI-assisted documentation workflows

1. **Doc agents must read source code, not just plans.** The plan listed "sync"
   as a line item. The agent inferred meaning from context instead of reading
   the implementation. A documentation agent should always verify its
   understanding against the actual source before writing.

2. **Fragment-first authoring requires extra scrutiny.** When the documentation
   architecture requires writing a shared fragment before the consuming page,
   the fragment becomes the agent's "source of truth" -- even though the agent
   just wrote it. This self-reinforcing loop can amplify hallucinations.

3. **Ambiguous terms need explicit definitions.** The word "sync" meant
   "synchronous" to the code author and "synchronize" to the doc author. Plan
   files and architecture docs should define domain-specific terms explicitly.

4. **Internal consistency is not correctness.** The hallucinated docs are
   internally consistent -- that's what makes them dangerous. Review processes
   should include cross-referencing docs against source code, not just checking
   whether the docs make sense on their own.

5. **Code agents and doc agents have different failure modes.** Code agents are
   constrained by compilers, test suites, and runtime behavior -- their output
   must be functionally correct. Doc agents have no such constraints. A doc
   agent can write any plausible-sounding text and nothing will fail. This
   asymmetry means documentation requires more rigorous verification, not less.

### For this project specifically

1. **The plan's extraction table was the right idea, poorly executed.** The plan
   correctly identified `sync-methods.md` as the source for the
   `sync-pattern.md` fragment. But the agent didn't follow through on reading
   that source. The extraction table should have included a brief summary of
   what each fragment should contain.

2. **CLAUDE.md should describe types, not just list them.** The architecture
   section listed `SyncConfig, SyncResult, SyncOperation` without explaining
   their fields or semantics. Adding one-line descriptions (e.g.,
   "`SyncConfig` — timeout and poll interval for sync polling") would have
   disambiguated the meaning of "sync."

## References

- Issue: [#44 — fix: sync package design is wrong](https://github.com/wphillipmoore/mq-rest-admin-java/issues/44)
- Correct code commit: `48bb4e9` (2026-02-13) — PR #18
- Hallucination origin: `247bf94` in mq-rest-admin-common (2026-02-14 13:20)
- Hallucination propagated: `a27f8ab` in mq-rest-admin-java (2026-02-14 13:38) — PR #41
- Python reference: `mq-rest-admin-python/src/pymqrest/sync.py`
- Go reference: `mq-rest-admin-go/mqrestadmin/session_sync.go`
