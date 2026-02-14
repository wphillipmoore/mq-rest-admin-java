# Command Methods

`MqRestSession` provides ~144 generated command methods, one for each MQSC
command verb + qualifier combination. Each method is a thin wrapper that calls
the internal command dispatcher with the correct verb and qualifier.

## Method signature pattern

```java
// DISPLAY commands return a list
List<Map<String, Object>> displayQueue(String name);
List<Map<String, Object>> displayQueue(String name, Map<String, Object> params);

// Non-DISPLAY commands return void
void defineQlocal(String name, Map<String, Object> params);
void alterChannel(String name, Map<String, Object> params);
void deleteQlocal(String name);

// Queue manager singletons return a single map
Map<String, Object> displayQmgr();
Map<String, Object> displayQmstatus();
```

## Parameters

| Parameter | Description |
| --- | --- |
| `name` | Object name or wildcard pattern (e.g. `"MY.QUEUE"`, `"APP.*"`) |
| `params` | Request attributes as key-value pairs |
| `responseParameters` | List of attribute names to include in the response |
| `where` | Filter expression for DISPLAY commands (e.g. `"current_depth GT 100"`) |

## Return values

- **DISPLAY commands**: `List<Map<String, Object>>` — one map per matched object.
  An empty list means no objects matched (not an error).
- **Queue manager singletons**: `Map<String, Object>` or `null`.
- **Non-DISPLAY commands**: `void` on success; throws `MqRestCommandException`
  on failure.

## Command categories

### Queue commands

`displayQueue`, `defineQlocal`, `defineQremote`, `defineQalias`, `defineQmodel`,
`alterQlocal`, `alterQremote`, `alterQalias`, `alterQmodel`,
`deleteQlocal`, `deleteQremote`, `deleteQalias`, `deleteQmodel`

### Channel commands

`displayChannel`, `defineChannel`, `defineSvrconn`, `alterChannel`,
`alterSvrconn`, `deleteChannel`, `deleteSvrconn`, `displayChstatus`

### Topic commands

`displayTopic`, `defineTopic`, `alterTopic`, `deleteTopic`,
`displayTopicstr`, `displayTpstatus`

### Queue manager commands

`displayQmgr`, `alterQmgr`, `displayQmstatus`

### Authentication commands

`displayAuthinfo`, `defineAuthinfo`, `alterAuthinfo`, `deleteAuthinfo`,
`displayChlauth`, `setChlauth`, `displayAuthrec`, `setAuthrec`,
`deleteAuthrec`, `displayEntauth`

### Other commands

`displayListener`, `defineListener`, `alterListener`, `deleteListener`,
`displayNamelist`, `defineNamelist`, `alterNamelist`, `deleteNamelist`,
`displayProcess`, `defineProcess`, `alterProcess`, `deleteProcess`,
`displayService`, `defineService`, `alterService`, `deleteService`,
`displaySub`, `defineSub`, `alterSub`, `deleteSub`

!!! note
    The full list of command methods is generated from the mapping data.
    See the [Qualifier Mapping Reference](../mappings/index.md) for per-qualifier details.
