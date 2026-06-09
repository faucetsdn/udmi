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

    service = new MosquittoDynamicSecurityService(endpoint, mockMqttClient, mockExecutor, mockScheduler);
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
    CompletableFuture<Void> future1 = new CompletableFuture<>();
    CommandRequest req1 = new CommandRequest("createClient", "{\"cmd\":\"1\"}".getBytes(StandardCharsets.UTF_8), future1);

    CompletableFuture<Void> future2 = new CompletableFuture<>();
    CommandRequest req2 = new CommandRequest("createRole", "{\"cmd\":\"2\"}".getBytes(StandardCharsets.UTF_8), future2);

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
    service.messageArrived("$CONTROL/dynamic-security/v1/response", responseMsg);

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
    CompletableFuture<Void> future1 = new CompletableFuture<>();
    CommandRequest req1 = new CommandRequest("createClient", "{\"cmd\":\"1\"}".getBytes(StandardCharsets.UTF_8), future1);

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
    CompletableFuture<Void> future2 = new CompletableFuture<>();
    CommandRequest req2 = new CommandRequest("createRole", "{\"cmd\":\"2\"}".getBytes(StandardCharsets.UTF_8), future2);
    service.enqueueCommand(req2);

    runExecutorTasks(); // runs drainAndPublishBatch
    verify(mockMqttClient).publish(eq("$CONTROL/dynamic-security/v1"), any(MqttMessage.class));
  }

  @Test
  void testQueueFullException() {
    // Fill the queue up to MAX_QUEUE_SIZE
    for (int i = 0; i < 10000; i++) {
      CompletableFuture<Void> f = new CompletableFuture<>();
      CommandRequest req = new CommandRequest("test", new byte[0], f);
      service.enqueueCommand(req);
    }

    // Now enqueue one more, which should fail immediately
    CompletableFuture<Void> failFuture = new CompletableFuture<>();
    CommandRequest extraReq = new CommandRequest("test", new byte[0], failFuture);
    service.enqueueCommand(extraReq);

    assertTrue(failFuture.isCompletedExceptionally());
    try {
      failFuture.get();
      fail("Expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof QueueFullException);
    }
  }

  @Test
  void testShutdownClearsPendingAndInFlightFutures() throws Exception {
    CompletableFuture<Void> inFlightFuture = new CompletableFuture<>();
    CommandRequest req1 = new CommandRequest("createClient", "{\"cmd\":\"1\"}".getBytes(StandardCharsets.UTF_8), inFlightFuture);

    CompletableFuture<Void> pendingFuture = new CompletableFuture<>();
    CommandRequest req2 = new CommandRequest("createRole", "{\"cmd\":\"2\"}".getBytes(StandardCharsets.UTF_8), pendingFuture);

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
