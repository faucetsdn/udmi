package com.google.bos.udmi.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.support.MosquittoDynamicSecurityService.CommandRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.EndpointConfiguration;

class MosquittoDynamicSecurityServiceTest {

  private EndpointConfiguration endpoint;
  private MqttClient mockMqttClient;
  private ExecutorService mockExecutor;
  private ScheduledExecutorService mockScheduler;
  private MosquittoDynamicSecurityService service;

  private List<Runnable> executorTasks;
  private List<ScheduledTaskInfo> scheduledTasks;

  private static class ScheduledTaskInfo {
    final Runnable runnable;
    final long delay;
    final TimeUnit unit;
    final ScheduledFuture<?> future;

    ScheduledTaskInfo(Runnable runnable, long delay, TimeUnit unit, ScheduledFuture<?> future) {
      this.runnable = runnable;
      this.delay = delay;
      this.unit = unit;
      this.future = future;
    }
  }

  @BeforeEach
  void setUp() {
    endpoint = new EndpointConfiguration();
    endpoint.hostname = "localhost";
    endpoint.port = 1883;

    mockMqttClient = mock(MqttClient.class);
    org.mockito.Mockito.when(mockMqttClient.getClientId()).thenReturn("test-client-id");
    mockExecutor = mock(ExecutorService.class);
    mockScheduler = mock(ScheduledExecutorService.class);

    executorTasks = new ArrayList<>();
    scheduledTasks = new ArrayList<>();

    // Mock executor to capture submitted runnables
    doAnswer(inv -> {
      Runnable r = inv.getArgument(0);
      executorTasks.add(r);
      return mock(Future.class);
    }).when(mockExecutor).submit(any(Runnable.class));

    // Mock scheduler to capture scheduled runnables
    doAnswer(inv -> {
      Runnable r = inv.getArgument(0);
      long delay = inv.getArgument(1);
      TimeUnit unit = inv.getArgument(2);
      ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
      scheduledTasks.add(new ScheduledTaskInfo(r, delay, unit, mockFuture));
      return mockFuture;
    }).when(mockScheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

    BlockingQueue<CommandRequest> queue = MosquittoDynamicSecurityService.createCommandQueue(100);
    service = new MosquittoDynamicSecurityService(
        endpoint,
        mockMqttClient,
        queue,
        2, // batchSizeLimit
        1024 * 1024, // batchBytesLimit
        2000, // minPublishIntervalMs
        30000, // batchTimeoutMs
        mockExecutor,
        mockScheduler
    );
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  private void runExecutorTasks() {
    List<Runnable> tasks = new ArrayList<>(executorTasks);
    executorTasks.clear();
    for (Runnable r : tasks) {
      r.run();
    }
  }

  private void runScheduledTasks() {
    List<ScheduledTaskInfo> tasks = new ArrayList<>(scheduledTasks);
    scheduledTasks.clear();
    for (ScheduledTaskInfo info : tasks) {
      info.runnable.run();
    }
  }

  @Test
  void testEnqueueAndSuccessfulResponse() throws Exception {
    CompletableFuture<udmi.schema.MosquittoClientResponse> future1 = new CompletableFuture<>();
    CommandRequest req1 = new CommandRequest(
        "createClient", "{\"cmd\":\"1\"}".getBytes(StandardCharsets.UTF_8), future1);

    CompletableFuture<udmi.schema.MosquittoClientResponse> future2 = new CompletableFuture<>();
    CommandRequest req2 = new CommandRequest(
        "createRole", "{\"cmd\":\"2\"}".getBytes(StandardCharsets.UTF_8), future2);

    // Enqueue commands
    service.enqueueCommand(req1);
    service.enqueueCommand(req2);

    // Run triggerWorker / drainAndPublishBatch task on executor
    runExecutorTasks();

    // Verify MQTT publish was called
    ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
    verify(mockMqttClient).publish(eq("$CONTROL/dynamic-security/v1"), messageCaptor.capture());

    MqttMessage publishedMessage = messageCaptor.getValue();
    assertNotNull(publishedMessage);
    String payloadStr = new String(publishedMessage.getPayload(), StandardCharsets.UTF_8);
    assertTrue(payloadStr.contains("{\"cmd\":\"1\"}"));
    assertTrue(payloadStr.contains("{\"cmd\":\"2\"}"));
    assertEquals(1, publishedMessage.getQos());

    // Verify a timeout task was scheduled
    assertEquals(1, scheduledTasks.size());
    assertEquals(30000, scheduledTasks.get(0).delay);

    // Simulate broker response
    String responseJson = "{\"responses\":[{\"status\":0},{\"status\":0}]}";
    MqttMessage responseMsg = new MqttMessage(responseJson.getBytes(StandardCharsets.UTF_8));

    // Call messageArrived (simulated callback from MQTT client)
    service.messageArrived("$CONTROL/dynamic-security/v1/response/test-client-id", responseMsg);

    // Run processResponses on executor
    runExecutorTasks();

    // Verify futures completed successfully
    assertTrue(future1.isDone());
    assertTrue(future2.isDone());
    future1.get(); // Should complete without throwing exception
    future2.get();
  }

  @Test
  void testBatchTimeoutAndRecovery() throws Exception {
    CompletableFuture<udmi.schema.MosquittoClientResponse> future1 = new CompletableFuture<>();
    CommandRequest req1 = new CommandRequest(
        "createClient", "{\"cmd\":\"1\"}".getBytes(StandardCharsets.UTF_8), future1);

    service.enqueueCommand(req1);
    runExecutorTasks(); // runs drainAndPublishBatch

    // Verify publish was called
    verify(mockMqttClient).publish(eq("$CONTROL/dynamic-security/v1"), any(MqttMessage.class));
    assertEquals(1, scheduledTasks.size());

    // Reset mock invocations before the next publish
    org.mockito.Mockito.clearInvocations(mockMqttClient);

    // Trigger timeout task manually
    runScheduledTasks();

    // Verify future failed exceptionally with TimeoutException
    assertTrue(future1.isCompletedExceptionally());
    try {
      future1.get();
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof java.util.concurrent.TimeoutException);
    }

    // Verify queue is recovered and we can publish again
    CompletableFuture<udmi.schema.MosquittoClientResponse> future2 = new CompletableFuture<>();
    CommandRequest req2 = new CommandRequest(
        "createRole", "{\"cmd\":\"2\"}".getBytes(StandardCharsets.UTF_8), future2);
    service.enqueueCommand(req2);

    runExecutorTasks(); // runs drainAndPublishBatch
    verify(mockMqttClient).publish(eq("$CONTROL/dynamic-security/v1"), any(MqttMessage.class));
  }

  @Test
  void testQueueFullException() {
    // Enqueue until the queue is full and we get an exceptionally completed future
    CompletableFuture<udmi.schema.MosquittoClientResponse> lastFuture = null;
    int count = 0;
    // Safety limit of 100,000 to prevent infinite loops in case of queue sizing issues
    while (count < 100000) {
      CompletableFuture<udmi.schema.MosquittoClientResponse> f = new CompletableFuture<>();
      CommandRequest req = new CommandRequest("test", new byte[0], f);
      CompletableFuture<udmi.schema.MosquittoClientResponse> res = service.enqueueCommand(req);
      if (res.isCompletedExceptionally()) {
        lastFuture = res;
        break;
      }
      count++;
    }

    assertNotNull(lastFuture, "Queue did not fill up within 100,000 enqueues");
    assertTrue(lastFuture.isCompletedExceptionally());
    try {
      lastFuture.get();
      fail("Expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof QueueFullException);
    }
  }

  @Test
  void testShutdownClearsPendingAndInFlightFutures() throws Exception {
    CompletableFuture<udmi.schema.MosquittoClientResponse> inFlightFuture =
        new CompletableFuture<>();
    CommandRequest req1 = new CommandRequest(
        "createClient", "{\"cmd\":\"1\"}".getBytes(StandardCharsets.UTF_8), inFlightFuture);

    CompletableFuture<udmi.schema.MosquittoClientResponse> pendingFuture =
        new CompletableFuture<>();
    CommandRequest req2 = new CommandRequest(
        "createRole", "{\"cmd\":\"2\"}".getBytes(StandardCharsets.UTF_8), pendingFuture);

    // Enqueue first command and let it go in-flight
    service.enqueueCommand(req1);
    runExecutorTasks(); // drainAndPublishBatch (req1 is now in-flight)

    // Enqueue second command, remains in queue (because batchInFlight is true)
    service.enqueueCommand(req2);

    // Call shutdown
    service.shutdown();

    // Verify both futures are completed exceptionally with "Service shutdown"
    assertTrue(inFlightFuture.isCompletedExceptionally());
    assertTrue(pendingFuture.isCompletedExceptionally());

    try {
      inFlightFuture.get();
      fail("Expected shutdown exception");
    } catch (ExecutionException e) {
      assertTrue(e.getCause().getMessage().contains("Service shutdown"));
    }

    try {
      pendingFuture.get();
      fail("Expected shutdown exception");
    } catch (ExecutionException e) {
      assertTrue(e.getCause().getMessage().contains("Service shutdown"));
    }
  }
}
