package io.github.wphillipmoore.mq.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.wphillipmoore.mq.rest.admin.auth.BasicAuth;
import io.github.wphillipmoore.mq.rest.admin.exception.MqRestTimeoutException;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncConfig;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncOperation;
import io.github.wphillipmoore.mq.rest.admin.sync.SyncResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MqRestSessionSyncTest {

  private static final String BASE_URL = "https://host:9443/ibmmq/rest/v2";
  private static final String QMGR = "QM1";

  @Mock private MqRestTransport transport;

  private MqRestSession session;
  private FakeClock fakeClock;

  /** Fake clock that advances time instantly on sleep() calls. */
  static final class FakeClock implements MqRestSession.Clock {
    private double elapsed;

    @Override
    public void sleep(double seconds) throws InterruptedException {
      elapsed += seconds;
    }

    @Override
    public double elapsedSeconds() {
      return elapsed;
    }

    @Override
    public void reset() {
      elapsed = 0;
    }
  }

  private TransportResponse emptyResponse() {
    return new TransportResponse(
        200,
        "{\"overallCompletionCode\":0,\"overallReasonCode\":0,\"commandResponse\":[]}",
        Map.of());
  }

  private TransportResponse statusResponse(String statusKey, String statusValue) {
    return new TransportResponse(
        200,
        "{\"overallCompletionCode\":0,\"overallReasonCode\":0,"
            + "\"commandResponse\":[{\"parameters\":{\""
            + statusKey
            + "\":\""
            + statusValue
            + "\"}}]}",
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
    fakeClock = new FakeClock();
    session.setClock(fakeClock);
  }

  // ---------------------------------------------------------------------------
  // hasStatus tests
  // ---------------------------------------------------------------------------

  @Nested
  class HasStatus {

    @Test
    void matchingStatusFoundInFirstKey() {
      List<Map<String, Object>> rows =
          List.of(Map.of("parameters", Map.of("channel_status", "RUNNING")));
      assertThat(
              MqRestSession.hasStatus(
                  rows, new String[] {"channel_status", "STATUS"}, MqRestSession.RUNNING_VALUES))
          .isTrue();
    }

    @Test
    void matchingStatusFoundInSecondKey() {
      List<Map<String, Object>> rows = List.of(Map.of("parameters", Map.of("STATUS", "STOPPED")));
      assertThat(
              MqRestSession.hasStatus(
                  rows, new String[] {"channel_status", "STATUS"}, MqRestSession.STOPPED_VALUES))
          .isTrue();
    }

    @Test
    void noMatchingStatusReturnsFalse() {
      List<Map<String, Object>> rows =
          List.of(Map.of("parameters", Map.of("channel_status", "RETRYING")));
      assertThat(
              MqRestSession.hasStatus(
                  rows, new String[] {"channel_status", "STATUS"}, MqRestSession.RUNNING_VALUES))
          .isFalse();
    }

    @Test
    void emptyRowsReturnsFalse() {
      assertThat(
              MqRestSession.hasStatus(
                  List.of(),
                  new String[] {"channel_status", "STATUS"},
                  MqRestSession.RUNNING_VALUES))
          .isFalse();
    }

    @Test
    void statusValueNotStringReturnsFalse() {
      List<Map<String, Object>> rows = List.of(Map.of("parameters", Map.of("STATUS", 42)));
      assertThat(
              MqRestSession.hasStatus(rows, new String[] {"STATUS"}, MqRestSession.RUNNING_VALUES))
          .isFalse();
    }

    @Test
    void caseSensitiveOnlyExactMatch() {
      List<Map<String, Object>> rows = List.of(Map.of("parameters", Map.of("STATUS", "Running")));
      assertThat(
              MqRestSession.hasStatus(rows, new String[] {"STATUS"}, MqRestSession.RUNNING_VALUES))
          .isFalse();
    }

    @Test
    void multipleRowsMatchInSecondRow() {
      List<Map<String, Object>> rows =
          List.of(
              Map.of("parameters", Map.of("STATUS", "RETRYING")),
              Map.of("parameters", Map.of("STATUS", "RUNNING")));
      assertThat(
              MqRestSession.hasStatus(rows, new String[] {"STATUS"}, MqRestSession.RUNNING_VALUES))
          .isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // Start → poll → success
  // ---------------------------------------------------------------------------

  @Nested
  class StartAndPoll {

    @Test
    void firstPollReturnsRunning() {
      // Call 1: START CHANNEL → empty
      // Call 2: DISPLAY CHSTATUS → RUNNING
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      SyncResult result = session.startChannelSync("MY.CHANNEL", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
      assertThat(result.polls()).isEqualTo(1);
      assertThat(result.elapsedSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void thirdPollReturnsRunning() {
      // Call 1: START CHANNEL → empty
      // Call 2: DISPLAY CHSTATUS → RETRYING
      // Call 3: DISPLAY CHSTATUS → RETRYING
      // Call 4: DISPLAY CHSTATUS → RUNNING
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RETRYING"))
          .thenReturn(statusResponse("channel_status", "RETRYING"))
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      SyncResult result = session.startChannelSync("MY.CHANNEL", new SyncConfig(10.0, 0.5));

      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
      assertThat(result.polls()).isEqualTo(3);
    }

    @Test
    void statusCommandErrorTreatedAsNotReady() {
      // Call 1: START → empty
      // Call 2: DISPLAY CHSTATUS → error (treated as empty)
      // Call 3: DISPLAY CHSTATUS → RUNNING
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(errorResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      SyncResult result = session.startChannelSync("MY.CHANNEL", new SyncConfig(10.0, 0.5));

      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
      assertThat(result.polls()).isEqualTo(2);
    }
  }

  // ---------------------------------------------------------------------------
  // Stop → poll → success
  // ---------------------------------------------------------------------------

  @Nested
  class StopAndPoll {

    @Test
    void firstPollReturnsStopped() {
      // Call 1: STOP CHANNEL → empty
      // Call 2: DISPLAY CHSTATUS → STOPPED
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "STOPPED"));

      SyncResult result = session.stopChannelSync("MY.CHANNEL", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
      assertThat(result.polls()).isEqualTo(1);
    }

    @Test
    void channelEmptyStatusRowsMeansStopped() {
      // Call 1: STOP CHANNEL → empty
      // Call 2: DISPLAY CHSTATUS → empty (channel has no status = stopped)
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse());

      SyncResult result = session.stopChannelSync("MY.CHANNEL", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
      assertThat(result.polls()).isEqualTo(1);
    }

    @Test
    void listenerEmptyStatusRowsContinuesPolling() {
      // For listeners, empty status does NOT mean stopped
      // Call 1: STOP LISTENER → empty
      // Call 2: DISPLAY LSSTATUS → empty (not stopped for listener)
      // Call 3: DISPLAY LSSTATUS → STOPPED
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"));

      SyncResult result = session.stopListenerSync("MY.LISTENER", new SyncConfig(10.0, 0.5));

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
      assertThat(result.polls()).isEqualTo(2);
    }

    @Test
    void lowercaseStoppedValueAccepted() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("STATUS", "stopped"));

      SyncResult result = session.stopServiceSync("MY.SVC", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
    }
  }

  // ---------------------------------------------------------------------------
  // Timeout
  // ---------------------------------------------------------------------------

  @Nested
  class Timeout {

    @Test
    void startPollingNeverSeesTargetThrowsTimeout() {
      // START + repeated RETRYING polls
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RETRYING"));

      assertThatThrownBy(() -> session.startChannelSync("MY.CHANNEL", new SyncConfig(2.0, 1.0)))
          .isInstanceOf(MqRestTimeoutException.class)
          .satisfies(
              ex -> {
                MqRestTimeoutException tex = (MqRestTimeoutException) ex;
                assertThat(tex.getName()).isEqualTo("MY.CHANNEL");
                assertThat(tex.getOperation()).isEqualTo("START");
                assertThat(tex.getElapsed()).isGreaterThanOrEqualTo(2.0);
              });
    }

    @Test
    void stopPollingNeverSeesTargetThrowsTimeout() {
      // STOP + repeated RUNNING polls
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      assertThatThrownBy(() -> session.stopListenerSync("MY.LISTENER", new SyncConfig(1.5, 0.5)))
          .isInstanceOf(MqRestTimeoutException.class)
          .satisfies(
              ex -> {
                MqRestTimeoutException tex = (MqRestTimeoutException) ex;
                assertThat(tex.getName()).isEqualTo("MY.LISTENER");
                assertThat(tex.getOperation()).isEqualTo("STOP");
              });
    }
  }

  // ---------------------------------------------------------------------------
  // Restart
  // ---------------------------------------------------------------------------

  @Nested
  class Restart {

    @Test
    void stopThenStartSucceeds() {
      // Call 1: STOP CHANNEL → empty
      // Call 2: DISPLAY CHSTATUS → STOPPED
      // Call 3: START CHANNEL → empty
      // Call 4: DISPLAY CHSTATUS → RUNNING
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      SyncResult result = session.restartChannel("MY.CHANNEL", new SyncConfig(10.0, 0.5));

      assertThat(result.operation()).isEqualTo(SyncOperation.RESTARTED);
      assertThat(result.polls()).isEqualTo(2); // 1 stop poll + 1 start poll
    }

    @Test
    void stopTimesOutStartNeverCalled() {
      // STOP + repeated RUNNING polls → timeout (never gets to START)
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      assertThatThrownBy(() -> session.restartChannel("MY.CHANNEL", new SyncConfig(1.0, 0.5)))
          .isInstanceOf(MqRestTimeoutException.class)
          .satisfies(
              ex -> {
                MqRestTimeoutException tex = (MqRestTimeoutException) ex;
                assertThat(tex.getOperation()).isEqualTo("STOP");
              });

      // Only STOP + poll calls, no START
      verify(transport, times(3)).postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean());
    }
  }

  // ---------------------------------------------------------------------------
  // Config handling
  // ---------------------------------------------------------------------------

  @Nested
  class ConfigHandling {

    @Test
    void nullConfigUsesDefaults() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      SyncResult result = session.startChannelSync("CH1", null);

      // Verifies it didn't throw — defaults were used
      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    }

    @Test
    void customConfigRespected() {
      // With a very short timeout, should fail faster
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RETRYING"));

      assertThatThrownBy(() -> session.startChannelSync("CH1", new SyncConfig(0.5, 0.3)))
          .isInstanceOf(MqRestTimeoutException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // Dispatch — verify each method uses correct qualifiers
  // ---------------------------------------------------------------------------

  @Nested
  class SyncMethodDispatch {

    @Test
    void startChannelSyncUsesChannelConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      session.startChannelSync("CH1", null);

      verifyCommandSequence("START", "CHANNEL", "DISPLAY", "CHSTATUS");
    }

    @Test
    void stopChannelSyncUsesChannelConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "STOPPED"));

      session.stopChannelSync("CH1", null);

      verifyCommandSequence("STOP", "CHANNEL", "DISPLAY", "CHSTATUS");
    }

    @Test
    void startListenerSyncUsesListenerConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      session.startListenerSync("LIS1", null);

      verifyCommandSequence("START", "LISTENER", "DISPLAY", "LSSTATUS");
    }

    @Test
    void stopListenerSyncUsesListenerConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"));

      session.stopListenerSync("LIS1", null);

      verifyCommandSequence("STOP", "LISTENER", "DISPLAY", "LSSTATUS");
    }

    @Test
    void startServiceSyncUsesServiceConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      session.startServiceSync("SVC1", null);

      verifyCommandSequence("START", "SERVICE", "DISPLAY", "SVSTATUS");
    }

    @Test
    void stopServiceSyncUsesServiceConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"));

      session.stopServiceSync("SVC1", null);

      verifyCommandSequence("STOP", "SERVICE", "DISPLAY", "SVSTATUS");
    }

    @Test
    void restartChannelUsesChannelConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      session.restartChannel("CH1", null);

      verifyRestartSequence("CHANNEL", "CHSTATUS");
    }

    @Test
    void restartListenerUsesListenerConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      session.restartListener("LIS1", null);

      verifyRestartSequence("LISTENER", "LSSTATUS");
    }

    @Test
    void restartServiceUsesServiceConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      session.restartService("SVC1", null);

      verifyRestartSequence("SERVICE", "SVSTATUS");
    }

    @SuppressWarnings("unchecked")
    private void verifyCommandSequence(
        String actionCommand,
        String actionQualifier,
        String statusCommand,
        String statusQualifier) {
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      List<Map<String, Object>> payloads = payloadCaptor.getAllValues();
      assertThat(payloads.get(0))
          .containsEntry("command", actionCommand)
          .containsEntry("qualifier", actionQualifier);
      assertThat(payloads.get(1))
          .containsEntry("command", statusCommand)
          .containsEntry("qualifier", statusQualifier);
    }

    @SuppressWarnings("unchecked")
    private void verifyRestartSequence(String qualifier, String statusQualifier) {
      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(4))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());
      List<Map<String, Object>> payloads = payloadCaptor.getAllValues();
      // STOP
      assertThat(payloads.get(0))
          .containsEntry("command", "STOP")
          .containsEntry("qualifier", qualifier);
      // DISPLAY *STATUS (stop poll)
      assertThat(payloads.get(1))
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", statusQualifier);
      // START
      assertThat(payloads.get(2))
          .containsEntry("command", "START")
          .containsEntry("qualifier", qualifier);
      // DISPLAY *STATUS (start poll)
      assertThat(payloads.get(3))
          .containsEntry("command", "DISPLAY")
          .containsEntry("qualifier", statusQualifier);
    }
  }

  // ---------------------------------------------------------------------------
  // Lowercase running/stopped values
  // ---------------------------------------------------------------------------

  @Nested
  class LowercaseStatusValues {

    @Test
    void lowercaseRunningDetected() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("STATUS", "running"));

      SyncResult result = session.startServiceSync("SVC1", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    }

    @Test
    void lowercaseStoppedDetected() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("STATUS", "stopped"));

      SyncResult result = session.stopServiceSync("SVC1", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
    }
  }

  // ---------------------------------------------------------------------------
  // Service empty status does not mean stopped
  // ---------------------------------------------------------------------------

  @Nested
  class ServiceEmptyStatus {

    @Test
    void serviceEmptyStatusContinuesPolling() {
      // For services, empty status does NOT mean stopped (emptyMeansStopped=false)
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"));

      SyncResult result = session.stopServiceSync("MY.SVC", new SyncConfig(10.0, 0.5));

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
      assertThat(result.polls()).isEqualTo(2);
    }
  }

  // ---------------------------------------------------------------------------
  // Channel empty status from error response
  // ---------------------------------------------------------------------------

  @Nested
  class ChannelErrorStatusMeansStopped {

    @Test
    void channelStatusCommandErrorTreatedAsEmptyMeansStopped() {
      // STOP → ok, DISPLAY CHSTATUS → error (treated as empty rows → stopped for channel)
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(errorResponse());

      SyncResult result = session.stopChannelSync("MY.CHANNEL", null);

      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
      assertThat(result.polls()).isEqualTo(1);
    }
  }

  // ---------------------------------------------------------------------------
  // Restart combined polls and elapsed
  // ---------------------------------------------------------------------------

  @Nested
  class RestartCombinedMetrics {

    @Test
    void restartCombinesPollsAndElapsed() {
      // STOP + 2 polls to stop + START + 1 poll to start
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse()) // STOP
          .thenReturn(statusResponse("channel_status", "RETRYING")) // poll 1 (not stopped)
          .thenReturn(statusResponse("channel_status", "STOPPED")) // poll 2 (stopped)
          .thenReturn(emptyResponse()) // START
          .thenReturn(statusResponse("channel_status", "RUNNING")); // poll 3 (running)

      SyncResult result = session.restartChannel("CH1", new SyncConfig(10.0, 0.5));

      assertThat(result.operation()).isEqualTo(SyncOperation.RESTARTED);
      assertThat(result.polls()).isEqualTo(3); // 2 stop polls + 1 start poll
    }
  }

  // ---------------------------------------------------------------------------
  // Name passed through correctly
  // ---------------------------------------------------------------------------

  @Nested
  class NamePassedThrough {

    @Test
    @SuppressWarnings("unchecked")
    void startPassesObjectName() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "RUNNING"));

      session.startChannelSync("MY.SPECIAL.CHANNEL", null);

      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(transport, times(2))
          .postJson(anyString(), payloadCaptor.capture(), anyMap(), any(), anyBoolean());

      // START command should have the name
      assertThat(payloadCaptor.getAllValues().get(0)).containsEntry("name", "MY.SPECIAL.CHANNEL");
      // DISPLAY CHSTATUS should also have the name
      assertThat(payloadCaptor.getAllValues().get(1)).containsEntry("name", "MY.SPECIAL.CHANNEL");
    }
  }

  // ---------------------------------------------------------------------------
  // SystemClock tests
  // ---------------------------------------------------------------------------

  @Nested
  class SystemClockTest {

    @Test
    void elapsedSecondsAfterResetIsNearZero() {
      MqRestSession.SystemClock clock = new MqRestSession.SystemClock();
      assertThat(clock.elapsedSeconds()).isLessThan(1.0);
    }

    @Test
    void resetResetsElapsed() {
      MqRestSession.SystemClock clock = new MqRestSession.SystemClock();
      clock.reset();
      assertThat(clock.elapsedSeconds()).isLessThan(1.0);
    }

    @Test
    void sleepAdvancesElapsed() throws InterruptedException {
      MqRestSession.SystemClock clock = new MqRestSession.SystemClock();
      clock.sleep(0.01); // 10ms
      assertThat(clock.elapsedSeconds()).isGreaterThan(0);
    }
  }

  // ---------------------------------------------------------------------------
  // InterruptedException handling
  // ---------------------------------------------------------------------------

  @Nested
  class InterruptHandling {

    @Test
    void startAndPollInterruptedThrowsTimeoutWithCause() {
      // Use a clock that throws InterruptedException
      MqRestSession.Clock interruptingClock =
          new MqRestSession.Clock() {
            @Override
            public void sleep(double seconds) throws InterruptedException {
              throw new InterruptedException("test interrupt");
            }

            @Override
            public double elapsedSeconds() {
              return 1.5;
            }

            @Override
            public void reset() {}
          };
      session.setClock(interruptingClock);

      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse());

      assertThatThrownBy(() -> session.startChannelSync("CH1", new SyncConfig(10.0, 1.0)))
          .isInstanceOf(MqRestTimeoutException.class)
          .hasMessageContaining("Interrupted")
          .hasCauseInstanceOf(InterruptedException.class);

      // Verify interrupt flag was set
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      // Clear the interrupt flag for test cleanup
      Thread.interrupted();
    }

    @Test
    void stopAndPollInterruptedThrowsTimeoutWithCause() {
      MqRestSession.Clock interruptingClock =
          new MqRestSession.Clock() {
            @Override
            public void sleep(double seconds) throws InterruptedException {
              throw new InterruptedException("test interrupt");
            }

            @Override
            public double elapsedSeconds() {
              return 2.0;
            }

            @Override
            public void reset() {}
          };
      session.setClock(interruptingClock);

      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse());

      assertThatThrownBy(() -> session.stopChannelSync("CH1", new SyncConfig(10.0, 1.0)))
          .isInstanceOf(MqRestTimeoutException.class)
          .hasMessageContaining("Interrupted")
          .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      Thread.interrupted();
    }
  }

  // ---------------------------------------------------------------------------
  // Null config branch coverage for remaining methods
  // ---------------------------------------------------------------------------

  @Nested
  class NullConfigBranch {

    @Test
    void stopChannelSyncNullConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "STOPPED"));

      SyncResult result = session.stopChannelSync("CH1", null);
      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
    }

    @Test
    void startListenerSyncNullConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.startListenerSync("LIS1", null);
      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    }

    @Test
    void restartListenerNullConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.restartListener("LIS1", null);
      assertThat(result.operation()).isEqualTo(SyncOperation.RESTARTED);
    }

    @Test
    void startServiceSyncNullConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.startServiceSync("SVC1", null);
      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    }

    @Test
    void restartServiceNullConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.restartService("SVC1", null);
      assertThat(result.operation()).isEqualTo(SyncOperation.RESTARTED);
    }

    @Test
    void stopChannelSyncWithConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("channel_status", "STOPPED"));

      SyncResult result = session.stopChannelSync("CH1", new SyncConfig(5.0, 0.5));
      assertThat(result.operation()).isEqualTo(SyncOperation.STOPPED);
    }

    @Test
    void startListenerSyncWithConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.startListenerSync("LIS1", new SyncConfig(5.0, 0.5));
      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    }

    @Test
    void restartListenerWithConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.restartListener("LIS1", new SyncConfig(5.0, 0.5));
      assertThat(result.operation()).isEqualTo(SyncOperation.RESTARTED);
    }

    @Test
    void startServiceSyncWithConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.startServiceSync("SVC1", new SyncConfig(5.0, 0.5));
      assertThat(result.operation()).isEqualTo(SyncOperation.STARTED);
    }

    @Test
    void restartServiceWithConfig() {
      when(transport.postJson(anyString(), anyMap(), anyMap(), any(), anyBoolean()))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "STOPPED"))
          .thenReturn(emptyResponse())
          .thenReturn(statusResponse("status", "RUNNING"));

      SyncResult result = session.restartService("SVC1", new SyncConfig(5.0, 0.5));
      assertThat(result.operation()).isEqualTo(SyncOperation.RESTARTED);
    }
  }
}
