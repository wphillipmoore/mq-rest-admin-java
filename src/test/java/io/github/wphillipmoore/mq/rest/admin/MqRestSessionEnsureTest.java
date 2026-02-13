package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureAction;
import io.github.wphillipmoore.mq.rest.admin.ensure.EnsureResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MqRestSessionEnsureTest {

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

  private TransportResponse objectResponse(String paramsJson) {
    return new TransportResponse(
        200,
        "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
            + "\"commandResponse\":[{\"parameters\":{"
            + paramsJson
            + "}}]}",
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

  // ---------------------------------------------------------------------------
  // valuesMatch tests
  // ---------------------------------------------------------------------------

  @Nested
  class ValuesMatch {

    @Test
    void matchingSameCase() {
      assertThat(MqRestSession.valuesMatch("ENABLED", "ENABLED")).isTrue();
    }

    @Test
    void matchingDifferentCase() {
      assertThat(MqRestSession.valuesMatch("enabled", "ENABLED")).isTrue();
    }

    @Test
    void integerMatchesStringEquivalent() {
      assertThat(MqRestSession.valuesMatch(5, "5")).isTrue();
    }

    @Test
    void whitespaceIsTrimmed() {
      assertThat(MqRestSession.valuesMatch("  value  ", "value")).isTrue();
    }

    @Test
    void currentNullReturnsFalse() {
      assertThat(MqRestSession.valuesMatch("value", null)).isFalse();
    }

    @Test
    void differentValuesReturnFalse() {
      assertThat(MqRestSession.valuesMatch("YES", "NO")).isFalse();
    }

    @Test
    void desiredNullCurrentNonNull() {
      assertThat(MqRestSession.valuesMatch(null, "value")).isFalse();
    }

    @Test
    void bothNullStringsMatch() {
      // String.valueOf(null) == "null", current is null → false
      assertThat(MqRestSession.valuesMatch(null, null)).isFalse();
    }

    @Test
    void doubleMatchesIntegerString() {
      assertThat(MqRestSession.valuesMatch(5.0, "5.0")).isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // ensureObject behavioral tests
  // ---------------------------------------------------------------------------

  @Nested
  class EnsureObjectCreated {

    @Test
    void displayThrowsCommandExceptionThenDefineCalledAndCreatedReturned() {
      // First call: DISPLAY throws error → DEFINE
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(errorResponse())
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureQlocal("Q1", Map.of("MAXDEPTH", 5000));

      assertThat(result.action()).isEqualTo(EnsureAction.CREATED);
      assertThat(result.changed()).isEmpty();
      verify(transport, times(2)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }

    @Test
    void displayReturnsEmptyThenDefineCalledAndCreatedReturned() {
      // DISPLAY returns empty → DEFINE
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureQlocal("Q1", Map.of("MAXDEPTH", 5000));

      assertThat(result.action()).isEqualTo(EnsureAction.CREATED);
      assertThat(result.changed()).isEmpty();
    }

    @Test
    void createdWithNullParamsStillDefines() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureQlocal("Q1", null);

      assertThat(result.action()).isEqualTo(EnsureAction.CREATED);
    }
  }

  @Nested
  class EnsureObjectUnchanged {

    @Test
    void objectExistsNullParamsReturnsUnchanged() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":5000"));

      EnsureResult result = session.ensureQlocal("Q1", null);

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
      assertThat(result.changed()).isEmpty();
      // Only DISPLAY called, no ALTER
      verify(transport, times(1)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }

    @Test
    void objectExistsEmptyParamsReturnsUnchanged() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":5000"));

      EnsureResult result = session.ensureQlocal("Q1", Map.of());

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
      verify(transport, times(1)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }

    @Test
    void objectExistsAllMatchReturnsUnchanged() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"5000\""));

      EnsureResult result = session.ensureQlocal("Q1", Map.of("MAXDEPTH", "5000"));

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
      assertThat(result.changed()).isEmpty();
      // Only DISPLAY called
      verify(transport, times(1)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }
  }

  @Nested
  class EnsureObjectUpdated {

    @Test
    void objectExistsSomeDifferAlterCalledWithOnlyChanged() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"5000\",\"DESCR\":\"old desc\""))
          .thenReturn(emptyResponse());

      Map<String, Object> params = new LinkedHashMap<>();
      params.put("MAXDEPTH", "5000"); // matches
      params.put("DESCR", "new desc"); // differs

      EnsureResult result = session.ensureQlocal("Q1", params);

      assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
      assertThat(result.changed()).containsExactly("DESCR");
      // DISPLAY + ALTER = 2 calls
      verify(transport, times(2)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }

    @Test
    void alterPayloadContainsOnlyChangedAttributes() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"5000\",\"DESCR\":\"old\""))
          .thenReturn(emptyResponse());

      Map<String, Object> params = new LinkedHashMap<>();
      params.put("MAXDEPTH", "5000");
      params.put("DESCR", "new");

      session.ensureQlocal("Q1", params);

      // Capture the ALTER call (second call)
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      Map<String, Object> alterPayload = payloadCaptor.getAllValues().get(1);

      @SuppressWarnings("unchecked")
      Map<String, Object> alterParams = (Map<String, Object>) alterPayload.get("parameters");
      assertThat(alterParams).containsEntry("DESCR", "new").doesNotContainKey("MAXDEPTH");
    }

    @Test
    void multipleChangedKeysReported() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"1000\",\"DESCR\":\"old\""))
          .thenReturn(emptyResponse());

      Map<String, Object> params = new LinkedHashMap<>();
      params.put("MAXDEPTH", "5000");
      params.put("DESCR", "new");

      EnsureResult result = session.ensureQlocal("Q1", params);

      assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
      assertThat(result.changed()).containsExactly("MAXDEPTH", "DESCR");
    }

    @Test
    void currentAttributeNullTriggersUpdate() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"5000\""))
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureQlocal("Q1", Map.of("DESCR", "new"));

      assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
      assertThat(result.changed()).containsExactly("DESCR");
    }
  }

  // ---------------------------------------------------------------------------
  // ensureQmgr special case
  // ---------------------------------------------------------------------------

  @Nested
  class EnsureQmgr {

    @Test
    void nullParamsReturnsUnchangedNoDisplay() {
      EnsureResult result = session.ensureQmgr(null);

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
      assertThat(result.changed()).isEmpty();
      // setUp stubbing is lenient; verify no calls for null params
      // (setUp already did one lenient setup but ensureQmgr(null) does no transport calls)
    }

    @Test
    void emptyParamsReturnsUnchangedNoDisplay() {
      EnsureResult result = session.ensureQmgr(Map.of());

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
    }

    @Test
    void paramsMatchReturnsUnchanged() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"DESCR\":\"my qmgr\""));

      EnsureResult result = session.ensureQmgr(Map.of("DESCR", "my qmgr"));

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
      // Only DISPLAY called
      verify(transport, times(1)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }

    @Test
    void paramsDifferReturnsUpdated() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"DESCR\":\"old\""))
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureQmgr(Map.of("DESCR", "new"));

      assertThat(result.action()).isEqualTo(EnsureAction.UPDATED);
      assertThat(result.changed()).containsExactly("DESCR");
      // DISPLAY + ALTER
      verify(transport, times(2)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }

    @Test
    void neverReturnsCreated() {
      // Even if display returns empty (shouldn't happen for QMGR in practice),
      // ensureQmgr still does compare logic → UNCHANGED with empty current
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureQmgr(Map.of("DESCR", "val"));

      // No CREATED — it treats empty display as empty map and finds differences
      assertThat(result.action()).isNotEqualTo(EnsureAction.CREATED);
    }

    @Test
    void dispatchesDisplayQmgr() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"DESCR\":\"val\""));

      session.ensureQmgr(Map.of("DESCR", "val"));

      Map<String, Object> payload = session.getLastCommandPayload();
      assertThat(payload).containsEntry("command", "DISPLAY").containsEntry("qualifier", "QMGR");
    }

    @Test
    void alterDispatchedWithOnlyChangedAttrs() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"DESCR\":\"old\",\"MAXDEPTH\":\"100\""))
          .thenReturn(emptyResponse());

      Map<String, Object> params = new LinkedHashMap<>();
      params.put("DESCR", "new");
      params.put("MAXDEPTH", "100");

      session.ensureQmgr(params);

      // The last command should be ALTER
      Map<String, Object> payload = session.getLastCommandPayload();
      assertThat(payload).containsEntry("command", "ALTER").containsEntry("qualifier", "QMGR");

      @SuppressWarnings("unchecked")
      Map<String, Object> alterParams = (Map<String, Object>) payload.get("parameters");
      assertThat(alterParams).containsEntry("DESCR", "new").doesNotContainKey("MAXDEPTH");
    }
  }

  // ---------------------------------------------------------------------------
  // Qualifier dispatch tests — one per ensure method
  // ---------------------------------------------------------------------------

  @Nested
  class EnsureQualifierDispatch {

    @Test
    void ensureQlocalDisplaysQueueDefinesQlocal() {
      // DISPLAY returns empty → DEFINE is called
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureQlocal("Q1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());

      // First call: DISPLAY QUEUE
      assertThat(payloadCaptor.getAllValues().get(0))
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", "QUEUE");
      // Second call: DEFINE QLOCAL
      assertThat(payloadCaptor.getAllValues().get(1))
          .containsEntry("command", "DEFINE")
          .containsEntry("qualifier", "QLOCAL");
    }

    @Test
    void ensureQremoteDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureQremote("Q1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "QUEUE");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "QREMOTE");
    }

    @Test
    void ensureQaliasDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureQalias("Q1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "QUEUE");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "QALIAS");
    }

    @Test
    void ensureQmodelDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureQmodel("Q1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "QUEUE");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "QMODEL");
    }

    @Test
    void ensureChannelDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureChannel("CH1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "CHANNEL");
      assertThat(payloadCaptor.getAllValues().get(1))
          .containsEntry("command", "DEFINE")
          .containsEntry("qualifier", "CHANNEL");
    }

    @Test
    void ensureAuthinfoDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureAuthinfo("AI1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "AUTHINFO");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "AUTHINFO");
    }

    @Test
    void ensureListenerDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureListener("L1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "LISTENER");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "LISTENER");
    }

    @Test
    void ensureNamelistDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureNamelist("NL1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "NAMELIST");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "NAMELIST");
    }

    @Test
    void ensureProcessDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureProcess("P1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "PROCESS");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "PROCESS");
    }

    @Test
    void ensureServiceDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureService("S1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "SERVICE");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "SERVICE");
    }

    @Test
    void ensureTopicDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureTopic("T1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "TOPIC");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "TOPIC");
    }

    @Test
    void ensureSubDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureSub("SUB1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "SUB");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "SUB");
    }

    @Test
    void ensureStgclassDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureStgclass("SC1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "STGCLASS");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "STGCLASS");
    }

    @Test
    void ensureComminfoDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureComminfo("CI1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "COMMINFO");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "COMMINFO");
    }

    @Test
    void ensureCfstructDispatch() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      session.ensureCfstruct("CF1", null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("qualifier", "CFSTRUCT");
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("qualifier", "CFSTRUCT");
    }
  }

  // ---------------------------------------------------------------------------
  // Alter qualifier dispatch — verify ALTER uses the right qualifier
  // ---------------------------------------------------------------------------

  @Nested
  class EnsureAlterQualifier {

    @Test
    void ensureQlocalAltersWithQlocal() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"1000\""))
          .thenReturn(emptyResponse());

      session.ensureQlocal("Q1", Map.of("MAXDEPTH", "5000"));

      Map<String, Object> payload = session.getLastCommandPayload();
      assertThat(payload).containsEntry("command", "ALTER").containsEntry("qualifier", "QLOCAL");
    }

    @Test
    void ensureQremoteAltersWithQremote() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"RNAME\":\"old\""))
          .thenReturn(emptyResponse());

      session.ensureQremote("Q1", Map.of("RNAME", "new"));

      Map<String, Object> payload = session.getLastCommandPayload();
      assertThat(payload).containsEntry("command", "ALTER").containsEntry("qualifier", "QREMOTE");
    }

    @Test
    void ensureQaliasAltersWithQalias() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"TARGET\":\"old\""))
          .thenReturn(emptyResponse());

      session.ensureQalias("Q1", Map.of("TARGET", "new"));

      Map<String, Object> payload = session.getLastCommandPayload();
      assertThat(payload).containsEntry("command", "ALTER").containsEntry("qualifier", "QALIAS");
    }

    @Test
    void ensureQmodelAltersWithQmodel() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"1000\""))
          .thenReturn(emptyResponse());

      session.ensureQmodel("Q1", Map.of("MAXDEPTH", "5000"));

      Map<String, Object> payload = session.getLastCommandPayload();
      assertThat(payload).containsEntry("command", "ALTER").containsEntry("qualifier", "QMODEL");
    }
  }

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------

  @Nested
  class EnsureEdgeCases {

    @Test
    void ensureWithCaseInsensitiveValueComparison() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"DESCR\":\"My Queue\""));

      EnsureResult result = session.ensureQlocal("Q1", Map.of("DESCR", "my queue"));

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
    }

    @Test
    void ensureWithIntToStringComparison() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"MAXDEPTH\":\"5000\""));

      EnsureResult result = session.ensureQlocal("Q1", Map.of("MAXDEPTH", 5000));

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
    }

    @Test
    void ensureDisplayPassesNameCorrectly() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(objectResponse("\"KEY\":\"VAL\""));

      session.ensureChannel("MY.CHANNEL", Map.of("KEY", "VAL"));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).containsEntry("name", "MY.CHANNEL");
    }

    @Test
    void ensureCreatedWithEmptyParams() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      EnsureResult result = session.ensureChannel("CH1", Map.of());

      assertThat(result.action()).isEqualTo(EnsureAction.CREATED);
    }

    @Test
    void ensureHandlesResponseWithoutParametersWrapper() {
      // Response item has attributes at top level (no "parameters" sub-object)
      TransportResponse topLevelResponse =
          new TransportResponse(
              200,
              "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                  + "\"commandResponse\":[{\"MAXDEPTH\":\"5000\"}]}",
              Map.of());
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(topLevelResponse);

      EnsureResult result = session.ensureQlocal("Q1", Map.of("MAXDEPTH", "5000"));

      assertThat(result.action()).isEqualTo(EnsureAction.UNCHANGED);
    }
  }

  @Nested
  class ExtractParametersMap {

    @Test
    void extractsFromParametersSubMap() {
      Map<String, Object> item = Map.of("parameters", Map.of("KEY", "VAL"));

      Map<String, Object> result = MqRestSession.extractParametersMap(item);

      assertThat(result).containsEntry("KEY", "VAL").doesNotContainKey("parameters");
    }

    @Test
    void returnsItemWhenNoParametersKey() {
      Map<String, Object> item = Map.of("KEY", "VAL");

      Map<String, Object> result = MqRestSession.extractParametersMap(item);

      assertThat(result).containsEntry("KEY", "VAL");
    }

    @Test
    void returnsItemWhenParametersIsNotMap() {
      Map<String, Object> item = Map.of("parameters", "not a map");

      Map<String, Object> result = MqRestSession.extractParametersMap(item);

      assertThat(result).containsEntry("parameters", "not a map");
    }
  }
}
