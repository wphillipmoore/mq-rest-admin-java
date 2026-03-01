package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.auth.CertificateAuth;
import io.github.wphillipmoore.mq.rest.admin.auth.LtpaAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestAuthException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestCommandException;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestResponseException;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingException;
import io.github.wphillipmoore.mq.rest.admin.mapping.MappingOverrideMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MqRestSessionTest {

  private static final String BASE_URL = "https://host:9443/ibmmq/rest/v2";
  private static final String QMGR = "QM1";

  @Mock private MqRestTransport transport;

  private TransportResponse successResponse(String body) {
    return new TransportResponse(200, body, Map.of());
  }

  private TransportResponse successResponseWithHeaders(String body, Map<String, String> headers) {
    return new TransportResponse(200, body, headers);
  }

  private String emptyCommandResponse() {
    return "{\"overallCompletionCode\":0,\"overallReasonCode\":0,\"commandResponse\":[]}";
  }

  private String commandResponseWithParams(String paramsJson) {
    return "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
        + "\"commandResponse\":[{\"parameters\":{"
        + paramsJson
        + "}}]}";
  }

  private MqRestSession.Builder basicBuilder() {
    return new MqRestSession.Builder(BASE_URL, QMGR, new BasicAuth("user", "pass"))
        .transport(transport);
  }

  private MqRestSession buildSessionNoMapping() {
    return basicBuilder().mapAttributes(false).build();
  }

  @Nested
  class BuilderValidation {

    @Test
    void buildSucceedsWithRequiredParams() {
      MqRestSession session = basicBuilder().build();

      assertThat(session.getQmgrName()).isEqualTo(QMGR);
    }

    @Test
    void builderNullRestBaseUrlThrowsNullPointerException() {
      assertThatThrownBy(() -> new MqRestSession.Builder(null, QMGR, new BasicAuth("u", "p")))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("restBaseUrl");
    }

    @Test
    void builderNullQmgrNameThrowsNullPointerException() {
      assertThatThrownBy(() -> new MqRestSession.Builder(BASE_URL, null, new BasicAuth("u", "p")))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("qmgrName");
    }

    @Test
    void builderNullCredentialsThrowsNullPointerException() {
      assertThatThrownBy(() -> new MqRestSession.Builder(BASE_URL, QMGR, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("credentials");
    }

    @Test
    void buildWithoutTransportThrowsNullPointerException() {
      assertThatThrownBy(
              () -> new MqRestSession.Builder(BASE_URL, QMGR, new BasicAuth("u", "p")).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessage("transport");
    }

    @Test
    void builderDefaultValues() {
      MqRestSession session = basicBuilder().build();

      assertThat(session.getGatewayQmgr()).isNull();
    }

    @Test
    void builderCustomGatewayQmgr() {
      MqRestSession session = basicBuilder().gatewayQmgr("GWQM").build();

      assertThat(session.getGatewayQmgr()).isEqualTo("GWQM");
    }

    @Test
    void builderStripsTrailingSlashesFromUrl() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session =
          new MqRestSession.Builder(BASE_URL + "///", QMGR, new BasicAuth("u", "p"))
              .transport(transport)
              .mapAttributes(false)
              .build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(transport).postJson(urlCaptor.capture(), anyMap(), anyMap(), any(), anyBoolean());
      assertThat(urlCaptor.getValue()).startsWith(BASE_URL + "/admin");
    }
  }

  @Nested
  class LtpaLogin {

    @Test
    void ltpaAuthPerformsLoginAtConstruction() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponseWithHeaders("{}", Map.of("Set-Cookie", "LtpaToken2=abc123; Path=/")));

      MqRestSession session =
          new MqRestSession.Builder(BASE_URL, QMGR, new LtpaAuth("user", "pass"))
              .transport(transport)
              .build();

      assertThat(session).isNotNull();
      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(transport).postJson(urlCaptor.capture(), anyMap(), anyMap(), any(), anyBoolean());
      assertThat(urlCaptor.getValue()).isEqualTo(BASE_URL + "/login");
    }

    @Test
    void ltpaLoginFailsOnHttpError() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(new TransportResponse(401, "Unauthorized", Map.of()));

      assertThatThrownBy(
              () ->
                  new MqRestSession.Builder(BASE_URL, QMGR, new LtpaAuth("user", "pass"))
                      .transport(transport)
                      .build())
          .isInstanceOf(MqRestAuthException.class)
          .hasMessageContaining("LTPA login failed");
    }

    @Test
    void ltpaLoginFailsWhenTokenMissing() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse("{}"));

      assertThatThrownBy(
              () ->
                  new MqRestSession.Builder(BASE_URL, QMGR, new LtpaAuth("user", "pass"))
                      .transport(transport)
                      .build())
          .isInstanceOf(MqRestAuthException.class)
          .hasMessageContaining("LtpaToken2");
    }

    @Test
    void basicAuthSkipsLogin() {
      MqRestSession session = basicBuilder().build();

      assertThat(session).isNotNull();
    }

    @Test
    void certificateAuthSkipsLogin() {
      MqRestSession session =
          new MqRestSession.Builder(BASE_URL, QMGR, new CertificateAuth("/cert.pem"))
              .transport(transport)
              .build();

      assertThat(session).isNotNull();
    }

    @Test
    void ltpaLoginSendsCsrfTokenInHeaders() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponseWithHeaders("{}", Map.of("Set-Cookie", "LtpaToken2=tok; Path=/")));

      new MqRestSession.Builder(BASE_URL, QMGR, new LtpaAuth("user", "pass"))
          .transport(transport)
          .csrfToken("mytoken")
          .build();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).containsEntry("ibm-mq-rest-csrf-token", "mytoken");
    }
  }

  @Nested
  class Headers {

    @Test
    void basicAuthIncludesAuthorizationHeader() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).containsKey("Authorization");
      assertThat(headersCaptor.getValue().get("Authorization")).startsWith("Basic ");
    }

    @Test
    void ltpaAuthIncludesCookieHeader() {
      // First call: LTPA login
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponseWithHeaders("{}", Map.of("Set-Cookie", "LtpaToken2=tok123; Path=/")))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session =
          new MqRestSession.Builder(BASE_URL, QMGR, new LtpaAuth("user", "pass"))
              .transport(transport)
              .mapAttributes(false)
              .build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      // The second call's headers should have Cookie
      List<Map<String, String>> allHeaders = headersCaptor.getAllValues();
      Map<String, String> commandHeaders = allHeaders.get(1);
      assertThat(commandHeaders).containsEntry("Cookie", "LtpaToken2=tok123");
    }

    @Test
    void certificateAuthOmitsAuthHeader() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session =
          new MqRestSession.Builder(BASE_URL, QMGR, new CertificateAuth("/cert.pem"))
              .transport(transport)
              .mapAttributes(false)
              .build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).doesNotContainKey("Authorization");
      assertThat(headersCaptor.getValue()).doesNotContainKey("Cookie");
    }

    @Test
    void csrfTokenIncludedByDefault() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue())
          .containsEntry("ibm-mq-rest-csrf-token", MqRestSession.DEFAULT_CSRF_TOKEN);
    }

    @Test
    void csrfTokenOmittedWhenNull() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().csrfToken(null).mapAttributes(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).doesNotContainKey("ibm-mq-rest-csrf-token");
    }

    @Test
    void gatewayHeaderIncludedWhenSet() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().gatewayQmgr("GWQM").mapAttributes(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).containsEntry(MqRestSession.GATEWAY_HEADER, "GWQM");
    }

    @Test
    void gatewayHeaderOmittedWhenNull() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).doesNotContainKey(MqRestSession.GATEWAY_HEADER);
    }
  }

  @Nested
  class MqscCommandFlow {

    @Test
    void simpleDisplayCommand() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(result).isEmpty();
    }

    @Test
    void commandWithName() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", "MY.QUEUE", null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).containsEntry("name", "MY.QUEUE");
    }

    @Test
    void commandWithoutNameOmitsNameField() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).doesNotContainKey("name");
    }

    @Test
    void commandWithRequestParameters() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("ALTER", "QUEUE", "Q1", Map.of("MAXDEPTH", 5000), null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat(params).containsEntry("MAXDEPTH", 5000);
    }

    @Test
    void commandWithResponseParameters() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, List.of("MAXDEPTH", "CURDEPTH"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("MAXDEPTH", "CURDEPTH");
    }

    @Test
    void displayWithNullResponseParamsDefaultsToAll() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("all");
    }

    @Test
    void nonDisplayWithNullResponseParamsOmitsField() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("ALTER", "QUEUE", "Q1", null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).doesNotContainKey("responseParameters");
    }

    @Test
    void commandUrlIsCorrect() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(transport).postJson(urlCaptor.capture(), anyMap(), anyMap(), any(), anyBoolean());
      assertThat(urlCaptor.getValue()).isEqualTo(BASE_URL + "/admin/action/qmgr/QM1/mqsc");
    }

    @Test
    void commandPayloadHasCorrectType() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue())
          .containsEntry("type", "runCommandJSON")
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", "QUEUE");
    }
  }

  @Nested
  class SessionState {

    @Test
    void stateIsNullBeforeFirstCommand() {
      MqRestSession session = basicBuilder().mapAttributes(false).build();

      assertThat(session.getLastHttpStatus()).isNull();
      assertThat(session.getLastResponseText()).isNull();
      assertThat(session.getLastResponsePayload()).isNull();
      assertThat(session.getLastCommandPayload()).isNull();
    }

    @Test
    void stateUpdatedAfterCommand() {
      String responseBody = emptyCommandResponse();
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(responseBody));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(session.getLastHttpStatus()).isEqualTo(200);
      assertThat(session.getLastResponseText()).isEqualTo(responseBody);
      assertThat(session.getLastResponsePayload()).isNotNull();
      assertThat(session.getLastCommandPayload()).isNotNull();
    }

    @Test
    void lastResponsePayloadIsUnmodifiable() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThatThrownBy(() -> session.getLastResponsePayload().put("new", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lastCommandPayloadIsUnmodifiable() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThatThrownBy(() -> session.getLastCommandPayload().put("new", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lastCommandPayloadContainsExpectedFields() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, null, null);

      assertThat(session.getLastCommandPayload())
          .containsEntry("type", "runCommandJSON")
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", "QUEUE")
          .containsEntry("name", "Q1");
    }
  }

  @Nested
  class ResponseParsing {

    @Test
    void invalidJsonThrowsResponseException() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse("not valid json{{{"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestResponseException.class)
          .hasMessageContaining("Invalid JSON");
    }

    @Test
    void nonObjectResponseThrowsResponseException() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse("[1, 2, 3]"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestResponseException.class)
          .hasMessageContaining("not a JSON object");
    }

    @Test
    void commandResponseNotListThrowsResponseException() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":\"not a list\"}"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestResponseException.class)
          .hasMessageContaining("commandResponse is not a list");
    }

    @Test
    void commandResponseItemNotObjectThrowsResponseException() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[\"not an object\"]}"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestResponseException.class)
          .hasMessageContaining("item is not an object");
    }
  }

  @Nested
  class ErrorDetection {

    @Test
    void overallErrorCodesThrowCommandException() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":2,\"overallReasonCode\":3008,"
                      + "\"commandResponse\":[]}"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestCommandException.class)
          .hasMessageContaining("MQSC command error");
    }

    @Test
    void perItemErrorCodesThrowCommandException() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[{\"completionCode\":2,\"reasonCode\":2085}]}"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestCommandException.class);
    }

    @Test
    void zeroCodesDoNotThrow() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[{\"completionCode\":0,\"reasonCode\":0}]}"));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(result).hasSize(1);
    }

    @Test
    void missingCodesDoNotThrow() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse("{\"commandResponse\":[{\"parameters\":{}}]}"));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(result).hasSize(1);
    }

    @Test
    void onlyReasonCodeNonZeroThrows() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":3008,"
                      + "\"commandResponse\":[]}"));

      MqRestSession session = buildSessionNoMapping();

      assertThatThrownBy(() -> session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null))
          .isInstanceOf(MqRestCommandException.class);
    }
  }

  @Nested
  class NestedObjectFlattening {

    @Test
    void flattenWithObjectsKey() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[{\"parameters\":{\"shared\":\"val\","
                      + "\"objects\":[{\"nested1\":\"a\"},{\"nested2\":\"b\"}]}}]}"));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(result).hasSize(2);
      assertThat(result.get(0)).containsEntry("shared", "val").containsEntry("nested1", "a");
      assertThat(result.get(1)).containsEntry("shared", "val").containsEntry("nested2", "b");
    }

    @Test
    void flattenWithoutObjectsKeyPassesThrough() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[{\"parameters\":{\"key\":\"value\"}}]}"));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0)).containsEntry("key", "value");
    }

    @Test
    void flattenMergesSharedFieldsWithNested() {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("type", "queue");
      item.put("objects", List.of(Map.of("name", "Q1"), Map.of("name", "Q2")));

      List<Map<String, Object>> result = MqRestSession.flattenNestedObjects(List.of(item));

      assertThat(result).hasSize(2);
      assertThat(result.get(0)).containsEntry("type", "queue").containsEntry("name", "Q1");
      assertThat(result.get(1)).containsEntry("type", "queue").containsEntry("name", "Q2");
      // Verify "objects" key is removed
      assertThat(result.get(0)).doesNotContainKey("objects");
    }
  }

  @Nested
  class AttributeMappingIntegration {

    @Test
    void mapAttributesTrueMapsResponseKeys() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[{\"parameters\":{\"MAXDEPTH\":5000}}]}"));

      MqRestSession session = basicBuilder().mapAttributes(true).mappingStrict(false).build();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, null, null);

      assertThat(result).hasSize(1);
      // Should have mapped MAXDEPTH → max_queue_depth
      assertThat(result.get(0)).containsKey("max_queue_depth");
    }

    @Test
    void mapAttributesFalsePreservesOriginalKeys() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponse(
                  "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
                      + "\"commandResponse\":[{\"parameters\":{\"MAXDEPTH\":5000}}]}"));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0)).containsKey("MAXDEPTH");
    }

    @Test
    void requestAttributesMappedWhenEnabled() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mapAttributes(true).mappingStrict(false).build();
      session.mqscCommand("ALTER", "QUEUE", "Q1", Map.of("max_queue_depth", 5000), null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat(params).containsKey("MAXDEPTH");
    }

    @Test
    void responseNormalizationUppercasesKeys() {
      Map<String, Object> attrs = Map.of("maxdepth", 5000, "CurDepth", 100);

      Map<String, Object> normalized = MqRestSession.normalizeResponseAttributes(attrs);

      assertThat(normalized).containsKey("MAXDEPTH").containsKey("CURDEPTH");
    }
  }

  @Nested
  class QualifierResolution {

    @Test
    void commandMapHitResolvesQualifier() {
      MqRestSession session = basicBuilder().mapAttributes(false).build();

      // "DISPLAY QUEUE" is in the default mapping data → "queue"
      String result = session.resolveMappingQualifier("DISPLAY", "QUEUE");

      assertThat(result).isEqualTo("queue");
    }

    @Test
    void fallbackMapResolvesQualifier() {
      MqRestSession session = basicBuilder().mapAttributes(false).build();

      // QLOCAL should fall back to DEFAULT_MAPPING_QUALIFIERS → "queue"
      String result = session.resolveMappingQualifier("DEFINE", "QLOCAL");

      assertThat(result).isEqualTo("queue");
    }

    @Test
    void lowercaseFallbackForUnknownQualifier() {
      MqRestSession session = basicBuilder().mapAttributes(false).build();

      String result = session.resolveMappingQualifier("DISPLAY", "UNKNOWN");

      assertThat(result).isEqualTo("unknown");
    }
  }

  @Nested
  class ResponseParameterMapping {

    @Test
    void allPassesThrough() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, List.of("all"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("all");
    }

    @Test
    void snakeCaseParamMappedToMqsc() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, List.of("max_queue_depth"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("MAXDEPTH");
    }

    @Test
    void unknownParamStrictThrows() {
      MqRestSession session = basicBuilder().mappingStrict(true).build();

      assertThatThrownBy(
              () ->
                  session.mqscCommand(
                      "DISPLAY", "QUEUE", "Q1", null, List.of("unknown_param"), null))
          .isInstanceOf(MappingException.class);
    }

    @Test
    void unknownParamPermissivePassesThrough() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, List.of("unknown_param"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("unknown_param");
    }

    @Test
    void unknownQualifierStrictThrowsForResponseParams() {
      MqRestSession session = basicBuilder().mappingStrict(true).build();

      assertThatThrownBy(
              () ->
                  session.mqscCommand("DISPLAY", "XYZOBJ", "X1", null, List.of("some_param"), null))
          .isInstanceOf(MappingException.class);
    }

    @Test
    void unknownQualifierPermissivePassesThroughResponseParams() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "XYZOBJ", "X1", null, List.of("some_param"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("some_param");
    }

    @Test
    void macroParameterMapped() {
      // Use mapping data with macros for DISPLAY QUEUE → CLUSINFO
      String json =
          "{\"commands\":{\"DISPLAY QUEUE\":{\"qualifier\":\"queue\","
              + "\"response_parameter_macros\":[\"CLUSINFO\"]}},"
              + "\"qualifiers\":{\"queue\":{\"request_key_map\":{},\"response_key_map\":{}}}}";
      Map<String, Object> overrides =
          new com.google.gson.Gson()
              .fromJson(
                  json, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());

      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session =
          basicBuilder()
              .mappingStrict(false)
              .mappingOverrides(overrides)
              .mappingOverridesMode(MappingOverrideMode.MERGE)
              .build();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, List.of("CLUSINFO"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("CLUSINFO");
    }
  }

  @Nested
  class WhereMapping {

    @Test
    void whereKeywordMapped() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", "*", null, null, "max_queue_depth GT 5000");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat(params).containsKey("WHERE");
      assertThat((String) params.get("WHERE")).isEqualTo("MAXDEPTH GT 5000");
    }

    @Test
    void whereKeywordWithoutRestPartMapped() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", "*", null, null, "max_queue_depth");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat((String) params.get("WHERE")).isEqualTo("MAXDEPTH");
    }

    @Test
    void unknownWhereKeywordStrictThrows() {
      MqRestSession session = basicBuilder().mappingStrict(true).build();

      assertThatThrownBy(
              () -> session.mqscCommand("DISPLAY", "QUEUE", "*", null, null, "unknown_key GT 100"))
          .isInstanceOf(MappingException.class);
    }

    @Test
    void unknownWhereKeywordPermissivePassesThrough() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", "*", null, null, "unknown_key GT 100");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat((String) params.get("WHERE")).isEqualTo("unknown_key GT 100");
    }

    @Test
    void blankWhereIsSkipped() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, "   ");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).doesNotContainKey("parameters");
    }

    @Test
    void nullWhereIsSkipped() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).doesNotContainKey("parameters");
    }

    @Test
    void unknownQualifierWhereStrictThrows() {
      MqRestSession session = basicBuilder().mappingStrict(true).build();

      assertThatThrownBy(
              () -> session.mqscCommand("DISPLAY", "XYZOBJ", "*", null, null, "some_key GT 100"))
          .isInstanceOf(MappingException.class);
    }

    @Test
    void mapWhereKeywordUnknownQualifierStrictThrowsDirectly() {
      MqRestSession session = basicBuilder().mappingStrict(true).build();

      assertThatThrownBy(() -> session.mapWhereKeyword("some_key GT 100", "nonexistent_qualifier"))
          .isInstanceOf(MappingException.class);
    }

    @Test
    void mapWhereKeywordUnknownQualifierPermissivePassesThroughDirectly() {
      MqRestSession session = basicBuilder().mappingStrict(false).build();

      String result = session.mapWhereKeyword("some_key GT 100", "nonexistent_qualifier");

      assertThat(result).isEqualTo("some_key GT 100");
    }
  }

  @Nested
  class StaticHelpers {

    @Test
    void buildBasicAuthHeaderEncodesCorrectly() {
      String header = MqRestSession.buildBasicAuthHeader("user", "pass");

      assertThat(header).isEqualTo("Basic dXNlcjpwYXNz");
    }

    @Test
    void buildBasicAuthHeaderHandlesSpecialCharacters() {
      String header = MqRestSession.buildBasicAuthHeader("user@domain", "p@ss:word");

      assertThat(header).startsWith("Basic ");
      String encoded = header.substring("Basic ".length());
      String decoded =
          new String(
              java.util.Base64.getDecoder().decode(encoded),
              java.nio.charset.StandardCharsets.UTF_8);
      assertThat(decoded).isEqualTo("user@domain:p@ss:word");
    }

    @Test
    void extractLtpaTokenFromSetCookie() {
      String[] result =
          MqRestSession.extractLtpaToken(Map.of("Set-Cookie", "LtpaToken2=mytoken; Path=/"));

      assertThat(result).isNotNull();
      assertThat(result[0]).isEqualTo("LtpaToken2");
      assertThat(result[1]).isEqualTo("mytoken");
    }

    @Test
    void extractLtpaTokenWithSuffixedCookieName() {
      String[] result =
          MqRestSession.extractLtpaToken(
              Map.of("Set-Cookie", "LtpaToken2_abcdef=suffixed_tok; Path=/"));

      assertThat(result).isNotNull();
      assertThat(result[0]).isEqualTo("LtpaToken2_abcdef");
      assertThat(result[1]).isEqualTo("suffixed_tok");
    }

    @Test
    void extractLtpaTokenCaseInsensitiveHeader() {
      String[] result =
          MqRestSession.extractLtpaToken(Map.of("set-cookie", "LtpaToken2=mytoken; Path=/"));

      assertThat(result).isNotNull();
      assertThat(result[1]).isEqualTo("mytoken");
    }

    @Test
    void extractLtpaTokenReturnsNullWhenNoSetCookie() {
      String[] result = MqRestSession.extractLtpaToken(Map.of("Content-Type", "text/html"));

      assertThat(result).isEmpty();
    }

    @Test
    void extractLtpaTokenReturnsNullWhenNameHasNoEquals() {
      String[] result = MqRestSession.extractLtpaToken(Map.of("Set-Cookie", "LtpaToken2; Path=/"));

      assertThat(result).isEmpty();
    }

    @Test
    void extractLtpaTokenReturnsNullWhenWrongCookieName() {
      String[] result =
          MqRestSession.extractLtpaToken(Map.of("Set-Cookie", "JSESSIONID=abc123; Path=/"));

      assertThat(result).isEmpty();
    }

    @Test
    void extractOptionalIntFromDouble() {
      Integer result = MqRestSession.extractOptionalInt(2.0);

      assertThat(result).isEqualTo(2);
    }

    @Test
    void extractOptionalIntFromInteger() {
      Integer result = MqRestSession.extractOptionalInt(42);

      assertThat(result).isEqualTo(42);
    }

    @Test
    void extractOptionalIntFromNullReturnsNull() {
      Integer result = MqRestSession.extractOptionalInt(null);

      assertThat(result).isEmpty();
    }

    @Test
    void extractOptionalIntFromStringReturnsNull() {
      Integer result = MqRestSession.extractOptionalInt("not a number");

      assertThat(result).isEmpty();
    }

    @Test
    void normalizeResponseParametersNullDisplayDefaultsToAll() {
      List<String> result = MqRestSession.normalizeResponseParameters(null, true);

      assertThat(result).containsExactly("all");
    }

    @Test
    void normalizeResponseParametersNullNonDisplayReturnsEmpty() {
      List<String> result = MqRestSession.normalizeResponseParameters(null, false);

      assertThat(result).isEmpty();
    }

    @Test
    void normalizeResponseParametersDetectsAllCaseInsensitive() {
      List<String> result =
          MqRestSession.normalizeResponseParameters(List.of("param1", "ALL"), false);

      assertThat(result).containsExactly("all");
    }

    @Test
    void normalizeResponseParametersCopiesList() {
      List<String> result =
          MqRestSession.normalizeResponseParameters(List.of("MAXDEPTH", "CURDEPTH"), true);

      assertThat(result).containsExactly("MAXDEPTH", "CURDEPTH");
    }

    @Test
    void parseResponsePayloadParsesValidJson() {
      Map<String, Object> result = MqRestSession.parseResponsePayload("{\"key\":\"value\"}");

      assertThat(result).containsEntry("key", "value");
    }

    @Test
    void buildCommandPayloadIncludesAllFields() {
      Map<String, Object> payload =
          MqRestSession.buildCommandPayload(
              "DISPLAY", "QUEUE", "Q1", Map.of("MAXDEPTH", 5000), List.of("all"));

      assertThat(payload)
          .containsEntry("type", "runCommandJSON")
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", "QUEUE")
          .containsEntry("name", "Q1");
      assertThat(payload).containsKey("parameters");
      assertThat(payload).containsKey("responseParameters");
    }

    @Test
    void buildCommandPayloadOmitsEmptyFields() {
      Map<String, Object> payload =
          MqRestSession.buildCommandPayload("ALTER", "QUEUE", null, Map.of(), List.of());

      assertThat(payload).doesNotContainKey("name");
      assertThat(payload).doesNotContainKey("parameters");
      assertThat(payload).doesNotContainKey("responseParameters");
    }

    @Test
    void buildCommandPayloadOmitsEmptyName() {
      Map<String, Object> payload =
          MqRestSession.buildCommandPayload("ALTER", "QUEUE", "", Map.of(), List.of());

      assertThat(payload).doesNotContainKey("name");
    }
  }

  @Nested
  class WhereWithMapping {

    @Test
    void whereNotAddedWhenMappingDisabledAndBlank() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, "");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue()).doesNotContainKey("parameters");
    }

    @Test
    void whereAddedWithoutMappingWhenMappingDisabled() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, "MAXDEPTH GT 5000");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat((String) params.get("WHERE")).isEqualTo("MAXDEPTH GT 5000");
    }
  }

  @Nested
  class MissingCommandResponse {

    @Test
    void missingCommandResponseReturnsEmptyList() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse("{\"overallCompletionCode\":0,\"overallReasonCode\":0}"));

      MqRestSession session = buildSessionNoMapping();
      List<Map<String, Object>> result =
          session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class TimeoutConfiguration {

    @Test
    void customTimeoutPassedToTransport() {
      Duration customTimeout = Duration.ofSeconds(60);
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().timeout(customTimeout).mapAttributes(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      ArgumentCaptor<Duration> timeoutCaptor = ArgumentCaptor.forClass(Duration.class);
      verify(transport)
          .postJson(anyString(), anyMap(), anyMap(), timeoutCaptor.capture(), anyBoolean());
      assertThat(timeoutCaptor.getValue()).isEqualTo(customTimeout);
    }

    @Test
    void nullTimeoutPassedToTransport() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().timeout(null).mapAttributes(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      ArgumentCaptor<Duration> timeoutCaptor = ArgumentCaptor.forClass(Duration.class);
      verify(transport)
          .postJson(anyString(), anyMap(), anyMap(), timeoutCaptor.capture(), anyBoolean());
      assertThat(timeoutCaptor.getValue()).isNull();
    }
  }

  @Nested
  class CommandNormalization {

    @Test
    void commandAndQualifierUppercased() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = buildSessionNoMapping();
      session.mqscCommand("display", "queue", null, null, null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      assertThat(payloadCaptor.getValue())
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", "QUEUE");
    }
  }

  @Nested
  class MappingOverrides {

    @Test
    void mappingOverridesApplied() {
      Map<String, Object> overrides = new LinkedHashMap<>();
      Map<String, Object> qualifiers = new LinkedHashMap<>();
      Map<String, Object> customQualifier = new LinkedHashMap<>();
      customQualifier.put("request_key_map", Map.of("custom_attr", "CUSTOMATTR"));
      customQualifier.put("response_key_map", Map.of("CUSTOMATTR", "custom_attr"));
      qualifiers.put("custom", customQualifier);
      overrides.put("qualifiers", qualifiers);
      overrides.put("commands", Map.of("DISPLAY CUSTOM", Map.of("qualifier", "custom")));

      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session =
          basicBuilder()
              .mappingOverrides(overrides)
              .mappingOverridesMode(MappingOverrideMode.MERGE)
              .mappingStrict(false)
              .build();
      session.mqscCommand("DISPLAY", "CUSTOM", "X1", Map.of("custom_attr", "val"), null, null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) payloadCaptor.getValue().get("parameters");
      assertThat(params).containsKey("CUSTOMATTR");
    }
  }

  @Nested
  class VerifyTls {

    @Test
    void verifyTlsPassedToTransport() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().verifyTls(false).mapAttributes(false).build();
      session.mqscCommand("DISPLAY", "QUEUE", null, null, null, null);

      ArgumentCaptor<Boolean> verifyCaptor = ArgumentCaptor.forClass(Boolean.class);
      verify(transport).postJson(anyString(), anyMap(), anyMap(), any(), verifyCaptor.capture());
      assertThat(verifyCaptor.getValue()).isFalse();
    }
  }

  @Nested
  class BranchCoverage {

    @Test
    void ltpaLoginWithNullCsrfTokenOmitsCsrfHeader() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(
              successResponseWithHeaders("{}", Map.of("Set-Cookie", "LtpaToken2=tok; Path=/")));

      new MqRestSession.Builder(BASE_URL, QMGR, new LtpaAuth("user", "pass"))
          .transport(transport)
          .csrfToken(null)
          .build();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), anyMap(), headersCaptor.capture(), any(), anyBoolean());
      assertThat(headersCaptor.getValue()).doesNotContainKey("ibm-mq-rest-csrf-token");
    }

    @Test
    void errorMessageIncludesCodesWhenOnlyCompletionCodePresent() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("overallCompletionCode", 2.0);
      payload.put("commandResponse", List.of());

      assertThatThrownBy(() -> MqRestSession.raiseForCommandErrors(payload, 200))
          .isInstanceOf(MqRestCommandException.class)
          .hasMessageContaining("overallCompletionCode");
    }

    @Test
    void errorMessageOmitsCodesWhenBothNull() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("commandResponse", List.of(Map.of("completionCode", 2.0, "reasonCode", 3008.0)));

      assertThatThrownBy(() -> MqRestSession.raiseForCommandErrors(payload, 200))
          .isInstanceOf(MqRestCommandException.class);
    }

    @Test
    void flattenSkipsNonMapNestedItems() {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("shared", "val");
      item.put("objects", List.of("not_a_map", Map.of("key", "value")));

      List<Map<String, Object>> result = MqRestSession.flattenNestedObjects(List.of(item));

      assertThat(result).hasSize(1);
      assertThat(result.get(0)).containsEntry("shared", "val").containsEntry("key", "value");
    }

    @Test
    void mapWhereKeywordKnownQualifierEmptyMapPermissivePassesThrough() {
      // Qualifier exists but has no key maps → snakeToMqsc is empty but hasQualifier is true
      // So the unknown qualifier branch is NOT taken, but keyword lookup returns null
      Map<String, Object> overrides = new LinkedHashMap<>();
      Map<String, Object> qualifiers = new LinkedHashMap<>();
      qualifiers.put("emptyq", Map.of());
      overrides.put("qualifiers", qualifiers);

      MqRestSession session =
          basicBuilder()
              .mappingStrict(false)
              .mappingOverrides(overrides)
              .mappingOverridesMode(MappingOverrideMode.MERGE)
              .build();

      String result = session.mapWhereKeyword("some_key GT 100", "emptyq");

      assertThat(result).isEqualTo("some_key GT 100");
    }

    @Test
    void mapResponseParametersStrictThrowsForUnknownParam() {
      // Use direct mqscCommand with a known qualifier but unknown response param
      MqRestSession session = basicBuilder().mappingStrict(true).build();

      assertThatThrownBy(
              () ->
                  session.mqscCommand(
                      "DISPLAY", "QUEUE", "Q1", null, List.of("unknown_param"), null))
          .isInstanceOf(MappingException.class);
    }

    @Test
    void nullMappingOverridesHandledGracefully() {
      MqRestSession session = basicBuilder().mappingOverrides(null).build();

      assertThat(session).isNotNull();
    }

    @Test
    void mapResponseParametersStrictWithAllKnownParamsSucceeds() {
      // Known params should pass through strict mapping without issues
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(successResponse(emptyCommandResponse()));

      MqRestSession session = basicBuilder().mappingStrict(true).build();
      session.mqscCommand("DISPLAY", "QUEUE", "Q1", null, List.of("max_queue_depth"), null);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport)
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      @SuppressWarnings("unchecked")
      List<String> respParams = (List<String>) payloadCaptor.getValue().get("responseParameters");
      assertThat(respParams).containsExactly("MAXDEPTH");
    }

    @Test
    void errorDetectionOnlyReasonCodeNonNullInPayload() {
      // overallCompletionCode null, overallReasonCode non-zero → should still show codes in message
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("overallReasonCode", 3008.0);
      payload.put("commandResponse", List.of());

      assertThatThrownBy(() -> MqRestSession.raiseForCommandErrors(payload, 200))
          .isInstanceOf(MqRestCommandException.class)
          .hasMessageContaining("overallReasonCode");
    }
  }
}
