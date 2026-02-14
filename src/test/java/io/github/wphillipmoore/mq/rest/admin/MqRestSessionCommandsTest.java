package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestCommandException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MqRestSessionCommandsTest {

  private static final String BASE_URL = "https://host:9443/ibmmq/rest/v2";
  private static final String QMGR = "QM1";

  @Mock private MqRestTransport transport;

  private MqRestSession session;

  private TransportResponse emptyResponse() {
    return new TransportResponse(
        200,
        "{\"overallCompletionCode\":0,\"overallReasonCode\":0,\"commandResponse\":[]}",
        Map.of());
  }

  private TransportResponse singleObjectResponse() {
    return new TransportResponse(
        200,
        "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
            + "\"commandResponse\":[{\"parameters\":{\"KEY\":\"VAL\"}}]}",
        Map.of());
  }

  private TransportResponse errorResponse() {
    return new TransportResponse(
        200,
        "{\"overallCompletionCode\":2,\"overallReasonCode\":3008,\"commandResponse\":[]}",
        Map.of());
  }

  @BeforeEach
  void setUp() {
    lenient()
        .when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
        .thenReturn(emptyResponse());
    session =
        new MqRestSession.Builder(BASE_URL, QMGR, new BasicAuth("user", "pass"))
            .transport(transport)
            .mapAttributes(false)
            .build();
  }

  private void assertDispatch(String command, String qualifier) {
    Map<String, Object> payload = session.getLastCommandPayload();
    assertThat(payload).containsEntry("command", command).containsEntry("qualifier", qualifier);
  }

  // ---------------------------------------------------------------------------
  // Pattern tests — detailed behavioral verification
  // ---------------------------------------------------------------------------

  @Nested
  class WildcardDisplayPattern {

    @Test
    void displayQueueWithNullNameDefaultsToWildcard() {
      session.displayQueue(null, null, null, null);

      assertThat(session.getLastCommandPayload()).containsEntry("name", "*");
    }

    @Test
    void displayQueueWithExplicitNamePassesName() {
      session.displayQueue("TEST.Q", null, null, null);

      assertThat(session.getLastCommandPayload()).containsEntry("name", "TEST.Q");
    }

    @Test
    void displayChannelWithNullNameDefaultsToWildcard() {
      session.displayChannel(null, null, null, null);

      assertThat(session.getLastCommandPayload()).containsEntry("name", "*");
    }

    @Test
    void displayChannelWithExplicitNamePassesName() {
      session.displayChannel("MY.CH", null, null, null);

      assertThat(session.getLastCommandPayload()).containsEntry("name", "MY.CH");
    }

    @Test
    void displayQueueReturnsList() {
      List<Map<String, Object>> result = session.displayQueue(null, null, null, null);

      assertThat(result).isNotNull();
    }
  }

  @Nested
  class OptionalNameDisplayPattern {

    @Test
    void displayApstatusWithNullNamePassesNull() {
      session.displayApstatus(null, null, null, null);

      assertThat(session.getLastCommandPayload()).doesNotContainKey("name");
    }

    @Test
    void displayApstatusWithNamePassesName() {
      session.displayApstatus("APP1", null, null, null);

      assertThat(session.getLastCommandPayload()).containsEntry("name", "APP1");
    }

    @Test
    void displayApstatusReturnsList() {
      List<Map<String, Object>> result = session.displayApstatus(null, null, null, null);

      assertThat(result).isNotNull();
    }
  }

  @Nested
  class SingletonDisplayPattern {

    @Test
    void displayQmgrReturnsFirstElement() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(singleObjectResponse());

      Map<String, Object> result = session.displayQmgr(null, null);

      assertThat(result).isNotNull().containsKey("parameters");
    }

    @Test
    void displayQmgrReturnsNullWhenEmpty() {
      Map<String, Object> result = session.displayQmgr(null, null);

      assertThat(result).isNull();
    }

    @Test
    void displayQmstatusReturnsFirstElement() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(singleObjectResponse());

      Map<String, Object> result = session.displayQmstatus(null, null);

      assertThat(result).isNotNull();
    }

    @Test
    void displayQmstatusReturnsNullWhenEmpty() {
      Map<String, Object> result = session.displayQmstatus(null, null);

      assertThat(result).isNull();
    }

    @Test
    void displayCmdservReturnsFirstElement() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(singleObjectResponse());

      Map<String, Object> result = session.displayCmdserv(null, null);

      assertThat(result).isNotNull();
    }

    @Test
    void displayCmdservReturnsNullWhenEmpty() {
      Map<String, Object> result = session.displayCmdserv(null, null);

      assertThat(result).isNull();
    }
  }

  @Nested
  class RequiredNamePattern {

    @Test
    void defineQlocalWithValidNameDispatches() {
      session.defineQlocal("Q1", null, null);

      assertDispatch("DEFINE", "QLOCAL");
      assertThat(session.getLastCommandPayload()).containsEntry("name", "Q1");
    }

    @Test
    void defineQlocalWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.defineQlocal(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }

    @Test
    void defineQremoteWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.defineQremote(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }

    @Test
    void defineQaliasWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.defineQalias(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }

    @Test
    void defineQmodelWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.defineQmodel(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }

    @Test
    void defineChannelWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.defineChannel(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }

    @Test
    void deleteQueueWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.deleteQueue(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }

    @Test
    void deleteChannelWithNullNameThrowsNullPointerException() {
      assertThatThrownBy(() -> session.deleteChannel(null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("name");
    }
  }

  @Nested
  class OptionalNameVoidPattern {

    @Test
    void defineAuthinfoWithNullNameDispatches() {
      session.defineAuthinfo(null, null, null);

      assertDispatch("DEFINE", "AUTHINFO");
      assertThat(session.getLastCommandPayload()).doesNotContainKey("name");
    }

    @Test
    void defineAuthinfoWithNameDispatches() {
      session.defineAuthinfo("AI1", null, null);

      assertDispatch("DEFINE", "AUTHINFO");
      assertThat(session.getLastCommandPayload()).containsEntry("name", "AI1");
    }
  }

  @Nested
  class NoNameVoidPattern {

    @Test
    void alterQmgrDispatchesWithoutName() {
      session.alterQmgr(null, null);

      assertDispatch("ALTER", "QMGR");
      assertThat(session.getLastCommandPayload()).doesNotContainKey("name");
    }

    @Test
    void startQmgrDispatchesWithoutName() {
      session.startQmgr(null, null);

      assertDispatch("START", "QMGR");
      assertThat(session.getLastCommandPayload()).doesNotContainKey("name");
    }
  }

  @Nested
  class ParameterForwarding {

    @Test
    void requestParametersForwardedToMqscCommand() {
      session.displayQueue("Q1", Map.of("FORCE", "YES"), null, null);

      @SuppressWarnings("unchecked")
      Map<String, Object> params =
          (Map<String, Object>) session.getLastCommandPayload().get("parameters");
      assertThat(params).containsEntry("FORCE", "YES");
    }

    @Test
    void responseParametersForwardedToMqscCommand() {
      session.displayQueue("Q1", null, List.of("MAXDEPTH"), null);

      @SuppressWarnings("unchecked")
      List<String> respParams =
          (List<String>) session.getLastCommandPayload().get("responseParameters");
      assertThat(respParams).containsExactly("MAXDEPTH");
    }

    @Test
    void whereClauseForwardedToMqscCommand() {
      session.displayQueue("*", null, null, "CURDEPTH GT 0");

      @SuppressWarnings("unchecked")
      Map<String, Object> params =
          (Map<String, Object>) session.getLastCommandPayload().get("parameters");
      assertThat(params).containsEntry("WHERE", "CURDEPTH GT 0");
    }
  }

  @Nested
  class ErrorPropagation {

    @Test
    void commandExceptionPropagatesFromDisplayMethod() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(errorResponse());

      assertThatThrownBy(() -> session.displayQueue(null, null, null, null))
          .isInstanceOf(MqRestCommandException.class);
    }

    @Test
    void commandExceptionPropagatesFromVoidMethod() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(errorResponse());

      assertThatThrownBy(() -> session.alterQmgr(null, null))
          .isInstanceOf(MqRestCommandException.class);
    }
  }

  @Nested
  class MappingIntegration {

    @Test
    void commandMethodsWorkWithMappingEnabled() {
      MqRestSession mappedSession =
          new MqRestSession.Builder(BASE_URL, QMGR, new BasicAuth("user", "pass"))
              .transport(transport)
              .mapAttributes(true)
              .mappingStrict(false)
              .build();

      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse());

      List<Map<String, Object>> result = mappedSession.displayQueue(null, null, null, null);

      assertThat(result).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // Exhaustive dispatch tests — verify every method dispatches correctly
  // ---------------------------------------------------------------------------

  @Nested
  class DisplayWildcardDispatch {

    @Test
    void displayQueueDispatches() {
      session.displayQueue(null, null, null, null);
      assertDispatch("DISPLAY", "QUEUE");
    }

    @Test
    void displayChannelDispatches() {
      session.displayChannel(null, null, null, null);
      assertDispatch("DISPLAY", "CHANNEL");
    }
  }

  @Nested
  class DisplaySingletonDispatch {

    @Test
    void displayQmgrDispatches() {
      session.displayQmgr(null, null);
      assertDispatch("DISPLAY", "QMGR");
    }

    @Test
    void displayQmstatusDispatches() {
      session.displayQmstatus(null, null);
      assertDispatch("DISPLAY", "QMSTATUS");
    }

    @Test
    void displayCmdservDispatches() {
      session.displayCmdserv(null, null);
      assertDispatch("DISPLAY", "CMDSERV");
    }
  }

  @Nested
  class DisplayOptionalNameDispatch {

    @Test
    void displayApstatusDispatches() {
      session.displayApstatus(null, null, null, null);
      assertDispatch("DISPLAY", "APSTATUS");
    }

    @Test
    void displayArchiveDispatches() {
      session.displayArchive(null, null, null, null);
      assertDispatch("DISPLAY", "ARCHIVE");
    }

    @Test
    void displayAuthinfoDispatches() {
      session.displayAuthinfo(null, null, null, null);
      assertDispatch("DISPLAY", "AUTHINFO");
    }

    @Test
    void displayAuthrecDispatches() {
      session.displayAuthrec(null, null, null, null);
      assertDispatch("DISPLAY", "AUTHREC");
    }

    @Test
    void displayAuthservDispatches() {
      session.displayAuthserv(null, null, null, null);
      assertDispatch("DISPLAY", "AUTHSERV");
    }

    @Test
    void displayCfstatusDispatches() {
      session.displayCfstatus(null, null, null, null);
      assertDispatch("DISPLAY", "CFSTATUS");
    }

    @Test
    void displayCfstructDispatches() {
      session.displayCfstruct(null, null, null, null);
      assertDispatch("DISPLAY", "CFSTRUCT");
    }

    @Test
    void displayChinitDispatches() {
      session.displayChinit(null, null, null, null);
      assertDispatch("DISPLAY", "CHINIT");
    }

    @Test
    void displayChlauthDispatches() {
      session.displayChlauth(null, null, null, null);
      assertDispatch("DISPLAY", "CHLAUTH");
    }

    @Test
    void displayChstatusDispatches() {
      session.displayChstatus(null, null, null, null);
      assertDispatch("DISPLAY", "CHSTATUS");
    }

    @Test
    void displayClusqmgrDispatches() {
      session.displayClusqmgr(null, null, null, null);
      assertDispatch("DISPLAY", "CLUSQMGR");
    }

    @Test
    void displayComminfoDispatches() {
      session.displayComminfo(null, null, null, null);
      assertDispatch("DISPLAY", "COMMINFO");
    }

    @Test
    void displayConnDispatches() {
      session.displayConn(null, null, null, null);
      assertDispatch("DISPLAY", "CONN");
    }

    @Test
    void displayEntauthDispatches() {
      session.displayEntauth(null, null, null, null);
      assertDispatch("DISPLAY", "ENTAUTH");
    }

    @Test
    void displayGroupDispatches() {
      session.displayGroup(null, null, null, null);
      assertDispatch("DISPLAY", "GROUP");
    }

    @Test
    void displayListenerDispatches() {
      session.displayListener(null, null, null, null);
      assertDispatch("DISPLAY", "LISTENER");
    }

    @Test
    void displayLogDispatches() {
      session.displayLog(null, null, null, null);
      assertDispatch("DISPLAY", "LOG");
    }

    @Test
    void displayLsstatusDispatches() {
      session.displayLsstatus(null, null, null, null);
      assertDispatch("DISPLAY", "LSSTATUS");
    }

    @Test
    void displayMaxsmsgsDispatches() {
      session.displayMaxsmsgs(null, null, null, null);
      assertDispatch("DISPLAY", "MAXSMSGS");
    }

    @Test
    void displayNamelistDispatches() {
      session.displayNamelist(null, null, null, null);
      assertDispatch("DISPLAY", "NAMELIST");
    }

    @Test
    void displayPolicyDispatches() {
      session.displayPolicy(null, null, null, null);
      assertDispatch("DISPLAY", "POLICY");
    }

    @Test
    void displayProcessDispatches() {
      session.displayProcess(null, null, null, null);
      assertDispatch("DISPLAY", "PROCESS");
    }

    @Test
    void displayPubsubDispatches() {
      session.displayPubsub(null, null, null, null);
      assertDispatch("DISPLAY", "PUBSUB");
    }

    @Test
    void displayQstatusDispatches() {
      session.displayQstatus(null, null, null, null);
      assertDispatch("DISPLAY", "QSTATUS");
    }

    @Test
    void displaySbstatusDispatches() {
      session.displaySbstatus(null, null, null, null);
      assertDispatch("DISPLAY", "SBSTATUS");
    }

    @Test
    void displaySecurityDispatches() {
      session.displaySecurity(null, null, null, null);
      assertDispatch("DISPLAY", "SECURITY");
    }

    @Test
    void displayServiceDispatches() {
      session.displayService(null, null, null, null);
      assertDispatch("DISPLAY", "SERVICE");
    }

    @Test
    void displaySmdsDispatches() {
      session.displaySmds(null, null, null, null);
      assertDispatch("DISPLAY", "SMDS");
    }

    @Test
    void displaySmdsconnDispatches() {
      session.displaySmdsconn(null, null, null, null);
      assertDispatch("DISPLAY", "SMDSCONN");
    }

    @Test
    void displayStgclassDispatches() {
      session.displayStgclass(null, null, null, null);
      assertDispatch("DISPLAY", "STGCLASS");
    }

    @Test
    void displaySubDispatches() {
      session.displaySub(null, null, null, null);
      assertDispatch("DISPLAY", "SUB");
    }

    @Test
    void displaySvstatusDispatches() {
      session.displaySvstatus(null, null, null, null);
      assertDispatch("DISPLAY", "SVSTATUS");
    }

    @Test
    void displaySystemDispatches() {
      session.displaySystem(null, null, null, null);
      assertDispatch("DISPLAY", "SYSTEM");
    }

    @Test
    void displayTclusterDispatches() {
      session.displayTcluster(null, null, null, null);
      assertDispatch("DISPLAY", "TCLUSTER");
    }

    @Test
    void displayThreadDispatches() {
      session.displayThread(null, null, null, null);
      assertDispatch("DISPLAY", "THREAD");
    }

    @Test
    void displayTopicDispatches() {
      session.displayTopic(null, null, null, null);
      assertDispatch("DISPLAY", "TOPIC");
    }

    @Test
    void displayTpstatusDispatches() {
      session.displayTpstatus(null, null, null, null);
      assertDispatch("DISPLAY", "TPSTATUS");
    }

    @Test
    void displayTraceDispatches() {
      session.displayTrace(null, null, null, null);
      assertDispatch("DISPLAY", "TRACE");
    }

    @Test
    void displayUsageDispatches() {
      session.displayUsage(null, null, null, null);
      assertDispatch("DISPLAY", "USAGE");
    }
  }

  @Nested
  class DefineDispatch {

    @Test
    void defineQlocalDispatches() {
      session.defineQlocal("Q1", null, null);
      assertDispatch("DEFINE", "QLOCAL");
    }

    @Test
    void defineQremoteDispatches() {
      session.defineQremote("Q1", null, null);
      assertDispatch("DEFINE", "QREMOTE");
    }

    @Test
    void defineQaliasDispatches() {
      session.defineQalias("Q1", null, null);
      assertDispatch("DEFINE", "QALIAS");
    }

    @Test
    void defineQmodelDispatches() {
      session.defineQmodel("Q1", null, null);
      assertDispatch("DEFINE", "QMODEL");
    }

    @Test
    void defineChannelDispatches() {
      session.defineChannel("CH1", null, null);
      assertDispatch("DEFINE", "CHANNEL");
    }

    @Test
    void defineAuthinfoDispatches() {
      session.defineAuthinfo(null, null, null);
      assertDispatch("DEFINE", "AUTHINFO");
    }

    @Test
    void defineBuffpoolDispatches() {
      session.defineBuffpool(null, null, null);
      assertDispatch("DEFINE", "BUFFPOOL");
    }

    @Test
    void defineCfstructDispatches() {
      session.defineCfstruct(null, null, null);
      assertDispatch("DEFINE", "CFSTRUCT");
    }

    @Test
    void defineComminfoDispatches() {
      session.defineComminfo(null, null, null);
      assertDispatch("DEFINE", "COMMINFO");
    }

    @Test
    void defineListenerDispatches() {
      session.defineListener(null, null, null);
      assertDispatch("DEFINE", "LISTENER");
    }

    @Test
    void defineLogDispatches() {
      session.defineLog(null, null, null);
      assertDispatch("DEFINE", "LOG");
    }

    @Test
    void defineMaxsmsgsDispatches() {
      session.defineMaxsmsgs(null, null, null);
      assertDispatch("DEFINE", "MAXSMSGS");
    }

    @Test
    void defineNamelistDispatches() {
      session.defineNamelist(null, null, null);
      assertDispatch("DEFINE", "NAMELIST");
    }

    @Test
    void defineProcessDispatches() {
      session.defineProcess(null, null, null);
      assertDispatch("DEFINE", "PROCESS");
    }

    @Test
    void definePsidDispatches() {
      session.definePsid(null, null, null);
      assertDispatch("DEFINE", "PSID");
    }

    @Test
    void defineServiceDispatches() {
      session.defineService(null, null, null);
      assertDispatch("DEFINE", "SERVICE");
    }

    @Test
    void defineStgclassDispatches() {
      session.defineStgclass(null, null, null);
      assertDispatch("DEFINE", "STGCLASS");
    }

    @Test
    void defineSubDispatches() {
      session.defineSub(null, null, null);
      assertDispatch("DEFINE", "SUB");
    }

    @Test
    void defineTopicDispatches() {
      session.defineTopic(null, null, null);
      assertDispatch("DEFINE", "TOPIC");
    }
  }

  @Nested
  class AlterDispatch {

    @Test
    void alterQmgrDispatches() {
      session.alterQmgr(null, null);
      assertDispatch("ALTER", "QMGR");
    }

    @Test
    void alterAuthinfoDispatches() {
      session.alterAuthinfo(null, null, null);
      assertDispatch("ALTER", "AUTHINFO");
    }

    @Test
    void alterBuffpoolDispatches() {
      session.alterBuffpool(null, null, null);
      assertDispatch("ALTER", "BUFFPOOL");
    }

    @Test
    void alterCfstructDispatches() {
      session.alterCfstruct(null, null, null);
      assertDispatch("ALTER", "CFSTRUCT");
    }

    @Test
    void alterChannelDispatches() {
      session.alterChannel(null, null, null);
      assertDispatch("ALTER", "CHANNEL");
    }

    @Test
    void alterComminfoDispatches() {
      session.alterComminfo(null, null, null);
      assertDispatch("ALTER", "COMMINFO");
    }

    @Test
    void alterListenerDispatches() {
      session.alterListener(null, null, null);
      assertDispatch("ALTER", "LISTENER");
    }

    @Test
    void alterNamelistDispatches() {
      session.alterNamelist(null, null, null);
      assertDispatch("ALTER", "NAMELIST");
    }

    @Test
    void alterProcessDispatches() {
      session.alterProcess(null, null, null);
      assertDispatch("ALTER", "PROCESS");
    }

    @Test
    void alterPsidDispatches() {
      session.alterPsid(null, null, null);
      assertDispatch("ALTER", "PSID");
    }

    @Test
    void alterSecurityDispatches() {
      session.alterSecurity(null, null, null);
      assertDispatch("ALTER", "SECURITY");
    }

    @Test
    void alterServiceDispatches() {
      session.alterService(null, null, null);
      assertDispatch("ALTER", "SERVICE");
    }

    @Test
    void alterSmdsDispatches() {
      session.alterSmds(null, null, null);
      assertDispatch("ALTER", "SMDS");
    }

    @Test
    void alterStgclassDispatches() {
      session.alterStgclass(null, null, null);
      assertDispatch("ALTER", "STGCLASS");
    }

    @Test
    void alterSubDispatches() {
      session.alterSub(null, null, null);
      assertDispatch("ALTER", "SUB");
    }

    @Test
    void alterTopicDispatches() {
      session.alterTopic(null, null, null);
      assertDispatch("ALTER", "TOPIC");
    }

    @Test
    void alterTraceDispatches() {
      session.alterTrace(null, null, null);
      assertDispatch("ALTER", "TRACE");
    }
  }

  @Nested
  class DeleteDispatch {

    @Test
    void deleteQueueDispatches() {
      session.deleteQueue("Q1", null, null);
      assertDispatch("DELETE", "QUEUE");
    }

    @Test
    void deleteChannelDispatches() {
      session.deleteChannel("CH1", null, null);
      assertDispatch("DELETE", "CHANNEL");
    }

    @Test
    void deleteAuthinfoDispatches() {
      session.deleteAuthinfo(null, null, null);
      assertDispatch("DELETE", "AUTHINFO");
    }

    @Test
    void deleteAuthrecDispatches() {
      session.deleteAuthrec(null, null, null);
      assertDispatch("DELETE", "AUTHREC");
    }

    @Test
    void deleteBuffpoolDispatches() {
      session.deleteBuffpool(null, null, null);
      assertDispatch("DELETE", "BUFFPOOL");
    }

    @Test
    void deleteCfstructDispatches() {
      session.deleteCfstruct(null, null, null);
      assertDispatch("DELETE", "CFSTRUCT");
    }

    @Test
    void deleteComminfoDispatches() {
      session.deleteComminfo(null, null, null);
      assertDispatch("DELETE", "COMMINFO");
    }

    @Test
    void deleteListenerDispatches() {
      session.deleteListener(null, null, null);
      assertDispatch("DELETE", "LISTENER");
    }

    @Test
    void deleteNamelistDispatches() {
      session.deleteNamelist(null, null, null);
      assertDispatch("DELETE", "NAMELIST");
    }

    @Test
    void deletePolicyDispatches() {
      session.deletePolicy(null, null, null);
      assertDispatch("DELETE", "POLICY");
    }

    @Test
    void deleteProcessDispatches() {
      session.deleteProcess(null, null, null);
      assertDispatch("DELETE", "PROCESS");
    }

    @Test
    void deletePsidDispatches() {
      session.deletePsid(null, null, null);
      assertDispatch("DELETE", "PSID");
    }

    @Test
    void deleteServiceDispatches() {
      session.deleteService(null, null, null);
      assertDispatch("DELETE", "SERVICE");
    }

    @Test
    void deleteStgclassDispatches() {
      session.deleteStgclass(null, null, null);
      assertDispatch("DELETE", "STGCLASS");
    }

    @Test
    void deleteSubDispatches() {
      session.deleteSub(null, null, null);
      assertDispatch("DELETE", "SUB");
    }

    @Test
    void deleteTopicDispatches() {
      session.deleteTopic(null, null, null);
      assertDispatch("DELETE", "TOPIC");
    }
  }

  @Nested
  class StartDispatch {

    @Test
    void startQmgrDispatches() {
      session.startQmgr(null, null);
      assertDispatch("START", "QMGR");
    }

    @Test
    void startCmdservDispatches() {
      session.startCmdserv(null, null);
      assertDispatch("START", "CMDSERV");
    }

    @Test
    void startChannelDispatches() {
      session.startChannel(null, null, null);
      assertDispatch("START", "CHANNEL");
    }

    @Test
    void startChinitDispatches() {
      session.startChinit(null, null, null);
      assertDispatch("START", "CHINIT");
    }

    @Test
    void startListenerDispatches() {
      session.startListener(null, null, null);
      assertDispatch("START", "LISTENER");
    }

    @Test
    void startServiceDispatches() {
      session.startService(null, null, null);
      assertDispatch("START", "SERVICE");
    }

    @Test
    void startSmdsconnDispatches() {
      session.startSmdsconn(null, null, null);
      assertDispatch("START", "SMDSCONN");
    }

    @Test
    void startTraceDispatches() {
      session.startTrace(null, null, null);
      assertDispatch("START", "TRACE");
    }
  }

  @Nested
  class StopDispatch {

    @Test
    void stopQmgrDispatches() {
      session.stopQmgr(null, null);
      assertDispatch("STOP", "QMGR");
    }

    @Test
    void stopCmdservDispatches() {
      session.stopCmdserv(null, null);
      assertDispatch("STOP", "CMDSERV");
    }

    @Test
    void stopChannelDispatches() {
      session.stopChannel(null, null, null);
      assertDispatch("STOP", "CHANNEL");
    }

    @Test
    void stopChinitDispatches() {
      session.stopChinit(null, null, null);
      assertDispatch("STOP", "CHINIT");
    }

    @Test
    void stopConnDispatches() {
      session.stopConn(null, null, null);
      assertDispatch("STOP", "CONN");
    }

    @Test
    void stopListenerDispatches() {
      session.stopListener(null, null, null);
      assertDispatch("STOP", "LISTENER");
    }

    @Test
    void stopServiceDispatches() {
      session.stopService(null, null, null);
      assertDispatch("STOP", "SERVICE");
    }

    @Test
    void stopSmdsconnDispatches() {
      session.stopSmdsconn(null, null, null);
      assertDispatch("STOP", "SMDSCONN");
    }

    @Test
    void stopTraceDispatches() {
      session.stopTrace(null, null, null);
      assertDispatch("STOP", "TRACE");
    }
  }

  @Nested
  class PingDispatch {

    @Test
    void pingQmgrDispatches() {
      session.pingQmgr(null, null);
      assertDispatch("PING", "QMGR");
    }

    @Test
    void pingChannelDispatches() {
      session.pingChannel(null, null, null);
      assertDispatch("PING", "CHANNEL");
    }
  }

  @Nested
  class ClearDispatch {

    @Test
    void clearQlocalDispatches() {
      session.clearQlocal(null, null, null);
      assertDispatch("CLEAR", "QLOCAL");
    }

    @Test
    void clearTopicstrDispatches() {
      session.clearTopicstr(null, null, null);
      assertDispatch("CLEAR", "TOPICSTR");
    }
  }

  @Nested
  class RefreshDispatch {

    @Test
    void refreshQmgrDispatches() {
      session.refreshQmgr(null, null);
      assertDispatch("REFRESH", "QMGR");
    }

    @Test
    void refreshClusterDispatches() {
      session.refreshCluster(null, null, null);
      assertDispatch("REFRESH", "CLUSTER");
    }

    @Test
    void refreshSecurityDispatches() {
      session.refreshSecurity(null, null, null);
      assertDispatch("REFRESH", "SECURITY");
    }
  }

  @Nested
  class ResetDispatch {

    @Test
    void resetQmgrDispatches() {
      session.resetQmgr(null, null);
      assertDispatch("RESET", "QMGR");
    }

    @Test
    void resetCfstructDispatches() {
      session.resetCfstruct(null, null, null);
      assertDispatch("RESET", "CFSTRUCT");
    }

    @Test
    void resetChannelDispatches() {
      session.resetChannel(null, null, null);
      assertDispatch("RESET", "CHANNEL");
    }

    @Test
    void resetClusterDispatches() {
      session.resetCluster(null, null, null);
      assertDispatch("RESET", "CLUSTER");
    }

    @Test
    void resetQstatsDispatches() {
      session.resetQstats(null, null, null);
      assertDispatch("RESET", "QSTATS");
    }

    @Test
    void resetSmdsDispatches() {
      session.resetSmds(null, null, null);
      assertDispatch("RESET", "SMDS");
    }

    @Test
    void resetTpipeDispatches() {
      session.resetTpipe(null, null, null);
      assertDispatch("RESET", "TPIPE");
    }
  }

  @Nested
  class ResolveDispatch {

    @Test
    void resolveChannelDispatches() {
      session.resolveChannel(null, null, null);
      assertDispatch("RESOLVE", "CHANNEL");
    }

    @Test
    void resolveIndoubtDispatches() {
      session.resolveIndoubt(null, null, null);
      assertDispatch("RESOLVE", "INDOUBT");
    }
  }

  @Nested
  class ResumeSuspendDispatch {

    @Test
    void resumeQmgrDispatches() {
      session.resumeQmgr(null, null);
      assertDispatch("RESUME", "QMGR");
    }

    @Test
    void suspendQmgrDispatches() {
      session.suspendQmgr(null, null);
      assertDispatch("SUSPEND", "QMGR");
    }
  }

  @Nested
  class SetDispatch {

    @Test
    void setArchiveDispatches() {
      session.setArchive(null, null, null);
      assertDispatch("SET", "ARCHIVE");
    }

    @Test
    void setAuthrecDispatches() {
      session.setAuthrec(null, null, null);
      assertDispatch("SET", "AUTHREC");
    }

    @Test
    void setChlauthDispatches() {
      session.setChlauth(null, null, null);
      assertDispatch("SET", "CHLAUTH");
    }

    @Test
    void setLogDispatches() {
      session.setLog(null, null, null);
      assertDispatch("SET", "LOG");
    }

    @Test
    void setPolicyDispatches() {
      session.setPolicy(null, null, null);
      assertDispatch("SET", "POLICY");
    }

    @Test
    void setSystemDispatches() {
      session.setSystem(null, null, null);
      assertDispatch("SET", "SYSTEM");
    }
  }

  @Nested
  class MiscellaneousDispatch {

    @Test
    void archiveLogDispatches() {
      session.archiveLog(null, null, null);
      assertDispatch("ARCHIVE", "LOG");
    }

    @Test
    void backupCfstructDispatches() {
      session.backupCfstruct(null, null, null);
      assertDispatch("BACKUP", "CFSTRUCT");
    }

    @Test
    void recoverBsdsDispatches() {
      session.recoverBsds(null, null, null);
      assertDispatch("RECOVER", "BSDS");
    }

    @Test
    void recoverCfstructDispatches() {
      session.recoverCfstruct(null, null, null);
      assertDispatch("RECOVER", "CFSTRUCT");
    }

    @Test
    void purgeChannelDispatches() {
      session.purgeChannel(null, null, null);
      assertDispatch("PURGE", "CHANNEL");
    }

    @Test
    void moveQlocalDispatches() {
      session.moveQlocal(null, null, null);
      assertDispatch("MOVE", "QLOCAL");
    }

    @Test
    void rverifySecurityDispatches() {
      session.rverifySecurity(null, null, null);
      assertDispatch("RVERIFY", "SECURITY");
    }
  }
}
