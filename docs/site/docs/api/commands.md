# Command Methods

## Overview

`MqRestSession` provides ~144 generated command methods, one for each MQSC
command verb + qualifier combination. Each method is a thin wrapper that calls
the internal command dispatcher with the correct verb and qualifier. Method
names follow the pattern `verbQualifier` in camelCase, mapping directly to
MQSC commands (e.g. `DISPLAY QUEUE` becomes `displayQueue()`).

## Method signature pattern

```java
// DISPLAY commands return a list
List<Map<String, Object>> displayQueue(String name);
List<Map<String, Object>> displayQueue(String name, Map<String, Object> params);
List<Map<String, Object>> displayQueue(
    String name, Map<String, Object> params,
    List<String> responseParameters, String where);

// Non-DISPLAY commands return void
void defineQlocal(String name, Map<String, Object> params);
void alterChannel(String name, Map<String, Object> params);
void deleteQlocal(String name);

// Queue manager singletons return a single map
Map<String, Object> displayQmgr();
Map<String, Object> displayQmgr(Map<String, Object> params, List<String> responseParameters);
Map<String, Object> displayQmstatus();
```

## Parameters

All methods accept these optional parameters:

| Parameter | Description |
| --- | --- |
| `name` | Object name or wildcard pattern (e.g. `"MY.QUEUE"`, `"APP.*"`). Required for most non-QMGR commands. |
| `params` | Request attributes as key-value pairs. Mapped from `snake_case` when mapping is enabled. |
| `responseParameters` | List of attribute names to include in the response. Defaults to `["all"]`. |
| `where` | Filter expression for DISPLAY commands (e.g. `"current_queue_depth GT 100"`). The keyword is mapped from `snake_case` when mapping is enabled. |

## Return values

- **DISPLAY commands**: `List<Map<String, Object>>` — one map per matched object.
  An empty list means no objects matched (not an error).
- **Queue manager singletons** (`displayQmgr`, `displayQmstatus`,
  `displayCmdserv`): `Map<String, Object>` or `null`.
- **Non-DISPLAY commands**: `void` on success; throws `MqRestCommandException`
  on failure.

## DISPLAY methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `displayApstatus()` | `DISPLAY APSTATUS` | apstatus |
| `displayArchive()` | `DISPLAY ARCHIVE` | archive |
| `displayAuthinfo()` | `DISPLAY AUTHINFO` | authinfo |
| `displayAuthrec()` | `DISPLAY AUTHREC` | authrec |
| `displayAuthserv()` | `DISPLAY AUTHSERV` | authserv |
| `displayCfstatus()` | `DISPLAY CFSTATUS` | cfstatus |
| `displayCfstruct()` | `DISPLAY CFSTRUCT` | cfstruct |
| `displayChannel()` | `DISPLAY CHANNEL` | channel |
| `displayChinit()` | `DISPLAY CHINIT` | chinit |
| `displayChlauth()` | `DISPLAY CHLAUTH` | chlauth |
| `displayChstatus()` | `DISPLAY CHSTATUS` | chstatus |
| `displayClusqmgr()` | `DISPLAY CLUSQMGR` | clusqmgr |
| `displayCmdserv()` | `DISPLAY CMDSERV` | cmdserv |
| `displayComminfo()` | `DISPLAY COMMINFO` | comminfo |
| `displayConn()` | `DISPLAY CONN` | conn |
| `displayEntauth()` | `DISPLAY ENTAUTH` | entauth |
| `displayGroup()` | `DISPLAY GROUP` | group |
| `displayListener()` | `DISPLAY LISTENER` | listener |
| `displayLog()` | `DISPLAY LOG` | log |
| `displayLsstatus()` | `DISPLAY LSSTATUS` | lsstatus |
| `displayMaxsmsgs()` | `DISPLAY MAXSMSGS` | maxsmsgs |
| `displayNamelist()` | `DISPLAY NAMELIST` | namelist |
| `displayPolicy()` | `DISPLAY POLICY` | policy |
| `displayProcess()` | `DISPLAY PROCESS` | process |
| `displayPubsub()` | `DISPLAY PUBSUB` | pubsub |
| `displayQmgr()` | `DISPLAY QMGR` | qmgr |
| `displayQmstatus()` | `DISPLAY QMSTATUS` | qmgr |
| `displayQstatus()` | `DISPLAY QSTATUS` | queue |
| `displayQueue()` | `DISPLAY QUEUE` | queue |
| `displaySbstatus()` | `DISPLAY SBSTATUS` | sbstatus |
| `displaySecurity()` | `DISPLAY SECURITY` | security |
| `displayService()` | `DISPLAY SERVICE` | service |
| `displaySmds()` | `DISPLAY SMDS` | smds |
| `displaySmdsconn()` | `DISPLAY SMDSCONN` | smdsconn |
| `displayStgclass()` | `DISPLAY STGCLASS` | stgclass |
| `displaySub()` | `DISPLAY SUB` | sub |
| `displaySvstatus()` | `DISPLAY SVSTATUS` | svstatus |
| `displayTcluster()` | `DISPLAY TCLUSTER` | tcluster |
| `displayThread()` | `DISPLAY THREAD` | thread |
| `displayTopic()` | `DISPLAY TOPIC` | topic |
| `displayTpstatus()` | `DISPLAY TPSTATUS` | tpstatus |
| `displayTrace()` | `DISPLAY TRACE` | trace |
| `displayUsage()` | `DISPLAY USAGE` | usage |

## DEFINE methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `defineAuthinfo()` | `DEFINE AUTHINFO` | authinfo |
| `defineBuffpool()` | `DEFINE BUFFPOOL` | buffpool |
| `defineCfstruct()` | `DEFINE CFSTRUCT` | cfstruct |
| `defineChannel()` | `DEFINE CHANNEL` | channel |
| `defineComminfo()` | `DEFINE COMMINFO` | comminfo |
| `defineListener()` | `DEFINE LISTENER` | listener |
| `defineLog()` | `DEFINE LOG` | log |
| `defineMaxsmsgs()` | `DEFINE MAXSMSGS` | maxsmsgs |
| `defineNamelist()` | `DEFINE NAMELIST` | namelist |
| `defineProcess()` | `DEFINE PROCESS` | process |
| `definePsid()` | `DEFINE PSID` | psid |
| `defineQalias()` | `DEFINE QALIAS` | queue |
| `defineQlocal()` | `DEFINE QLOCAL` | queue |
| `defineQmodel()` | `DEFINE QMODEL` | queue |
| `defineQremote()` | `DEFINE QREMOTE` | queue |
| `defineService()` | `DEFINE SERVICE` | service |
| `defineStgclass()` | `DEFINE STGCLASS` | stgclass |
| `defineSub()` | `DEFINE SUB` | sub |
| `defineTopic()` | `DEFINE TOPIC` | topic |

## DELETE methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `deleteAuthinfo()` | `DELETE AUTHINFO` | authinfo |
| `deleteAuthrec()` | `DELETE AUTHREC` | authrec |
| `deleteBuffpool()` | `DELETE BUFFPOOL` | buffpool |
| `deleteCfstruct()` | `DELETE CFSTRUCT` | cfstruct |
| `deleteChannel()` | `DELETE CHANNEL` | channel |
| `deleteComminfo()` | `DELETE COMMINFO` | comminfo |
| `deleteListener()` | `DELETE LISTENER` | listener |
| `deleteNamelist()` | `DELETE NAMELIST` | namelist |
| `deletePolicy()` | `DELETE POLICY` | policy |
| `deleteProcess()` | `DELETE PROCESS` | process |
| `deletePsid()` | `DELETE PSID` | psid |
| `deleteQueue()` | `DELETE QUEUE` | queue |
| `deleteService()` | `DELETE SERVICE` | service |
| `deleteStgclass()` | `DELETE STGCLASS` | stgclass |
| `deleteSub()` | `DELETE SUB` | sub |
| `deleteTopic()` | `DELETE TOPIC` | topic |

## ALTER methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `alterAuthinfo()` | `ALTER AUTHINFO` | authinfo |
| `alterBuffpool()` | `ALTER BUFFPOOL` | buffpool |
| `alterCfstruct()` | `ALTER CFSTRUCT` | cfstruct |
| `alterChannel()` | `ALTER CHANNEL` | channel |
| `alterComminfo()` | `ALTER COMMINFO` | comminfo |
| `alterListener()` | `ALTER LISTENER` | listener |
| `alterNamelist()` | `ALTER NAMELIST` | namelist |
| `alterProcess()` | `ALTER PROCESS` | process |
| `alterPsid()` | `ALTER PSID` | psid |
| `alterQmgr()` | `ALTER QMGR` | qmgr |
| `alterSecurity()` | `ALTER SECURITY` | security |
| `alterService()` | `ALTER SERVICE` | service |
| `alterSmds()` | `ALTER SMDS` | smds |
| `alterStgclass()` | `ALTER STGCLASS` | stgclass |
| `alterSub()` | `ALTER SUB` | sub |
| `alterTopic()` | `ALTER TOPIC` | topic |
| `alterTrace()` | `ALTER TRACE` | trace |

## SET methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `setArchive()` | `SET ARCHIVE` | archive |
| `setAuthrec()` | `SET AUTHREC` | authrec |
| `setChlauth()` | `SET CHLAUTH` | chlauth |
| `setLog()` | `SET LOG` | log |
| `setPolicy()` | `SET POLICY` | policy |

## START methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `startChannel()` | `START CHANNEL` | channel |
| `startChinit()` | `START CHINIT` | chinit |
| `startCmdserv()` | `START CMDSERV` | cmdserv |
| `startListener()` | `START LISTENER` | listener |
| `startQmgr()` | `START QMGR` | qmgr |
| `startService()` | `START SERVICE` | service |
| `startSmdsconn()` | `START SMDSCONN` | smdsconn |
| `startTrace()` | `START TRACE` | trace |

## STOP methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `stopChannel()` | `STOP CHANNEL` | channel |
| `stopChinit()` | `STOP CHINIT` | chinit |
| `stopCmdserv()` | `STOP CMDSERV` | cmdserv |
| `stopConn()` | `STOP CONN` | conn |
| `stopListener()` | `STOP LISTENER` | listener |
| `stopQmgr()` | `STOP QMGR` | qmgr |
| `stopService()` | `STOP SERVICE` | service |
| `stopSmdsconn()` | `STOP SMDSCONN` | smdsconn |
| `stopTrace()` | `STOP TRACE` | trace |

## Other methods

| Method | MQSC command | Qualifier mapping |
| --- | --- | --- |
| `archiveLog()` | `ARCHIVE LOG` | log |
| `backupCfstruct()` | `BACKUP CFSTRUCT` | cfstruct |
| `clearQlocal()` | `CLEAR QLOCAL` | queue |
| `clearTopicstr()` | `CLEAR TOPICSTR` | topicstr |
| `moveQlocal()` | `MOVE QLOCAL` | queue |
| `pingChannel()` | `PING CHANNEL` | channel |
| `pingQmgr()` | `PING QMGR` | qmgr |
| `purgeChannel()` | `PURGE CHANNEL` | channel |
| `recoverBsds()` | `RECOVER BSDS` | bsds |
| `recoverCfstruct()` | `RECOVER CFSTRUCT` | cfstruct |
| `refreshCluster()` | `REFRESH CLUSTER` | cluster |
| `refreshQmgr()` | `REFRESH QMGR` | qmgr |
| `refreshSecurity()` | `REFRESH SECURITY` | security |
| `resetCfstruct()` | `RESET CFSTRUCT` | cfstruct |
| `resetChannel()` | `RESET CHANNEL` | channel |
| `resetCluster()` | `RESET CLUSTER` | cluster |
| `resetQmgr()` | `RESET QMGR` | qmgr |
| `resetQstats()` | `RESET QSTATS` | queue |
| `resetSmds()` | `RESET SMDS` | smds |
| `resetTpipe()` | `RESET TPIPE` | tpipe |
| `resolveChannel()` | `RESOLVE CHANNEL` | channel |
| `resolveIndoubt()` | `RESOLVE INDOUBT` | indoubt |
| `resumeQmgr()` | `RESUME QMGR` | qmgr |
| `rverifySecurity()` | `RVERIFY SECURITY` | security |
| `suspendQmgr()` | `SUSPEND QMGR` | qmgr |

!!! note
    The full list of command methods is generated from the mapping data.
    See the [Qualifier Mapping Reference](../mappings/index.md) for per-qualifier
    details including attribute names and value mappings for each object type.
