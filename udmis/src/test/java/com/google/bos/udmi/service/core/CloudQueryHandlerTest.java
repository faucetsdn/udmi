package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.ProtocolFamily;
import udmi.schema.CloudModel;
import udmi.schema.CloudQuery;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations.Depth;
import udmi.schema.Envelope;

class CloudQueryHandlerTest implements MessageContinuation {

  private static final Instant LAST_SEEN = Instant.ofEpochSecond(91872);
  private static final Date QUERY_GENERATION = new Date();
  private final ControlProcessor controlProcessor = mock(ControlProcessor.class);
  private final Envelope envelope = new Envelope();
  private final ArgumentCaptor<Object> targetCapture = ArgumentCaptor.forClass(Object.class);
  private final ArgumentCaptor<Object> controlCapture = ArgumentCaptor.forClass(Object.class);
  private final ArgumentCaptor<Envelope> envelopeCapture = ArgumentCaptor.forClass(Envelope.class);
  private final Set<String> mockRegistries = ImmutableSet.of(TEST_REGISTRY);
  private final CloudQuery query = new CloudQuery();
  private CloudQueryHandler queryHandler;

  @Override
  public Envelope getEnvelope() {
    return envelope;
  }

  @Override
  public void publish(Object message) {
    throw new RuntimeException("Should be mocked");
  }

  @Test
  public void queryAllRegistries() {
    queryHandler.process();

    List<Object> targetMessages = targetCapture.getAllValues();
    assertEquals(1, targetMessages.size(), "published messages");
    DiscoveryEvents registryDiscovery = (DiscoveryEvents) targetMessages.get(0);
    assertEquals(ProtocolFamily.IOT, registryDiscovery.scan_family);
    assertEquals(1, registryDiscovery.registries.size(), "discovered registries");
    assertEquals(QUERY_GENERATION, registryDiscovery.generation, "discovery generation");
    CloudModel cloudModel = registryDiscovery.registries.get(TEST_REGISTRY);
    assertEquals(LAST_SEEN, cloudModel.last_event_time.toInstant(), "last seen time");

    List<Object> controlMessages = controlCapture.getAllValues();
    assertEquals(1, controlMessages.size(), "control messages");
    CloudQuery cloudQuery = (CloudQuery) controlMessages.get(0);
    assertEquals(QUERY_GENERATION, cloudQuery.generation, "control query generation");
    List<Envelope> targetEnvelope = envelopeCapture.getAllValues();
    assertEquals(1, targetEnvelope.size(), "control envelopes");
    Envelope controlEnvelope = targetEnvelope.get(0);
    assertEquals(TEST_REGISTRY, controlEnvelope.deviceRegistryId, "control message registry");
  }

  @BeforeEach
  public void setupMock() {
    query.generation = QUERY_GENERATION;
    query.depth = Depth.ENTRIES;

    doReturn(this).when(controlProcessor).getContinuation(eq(query));
    doNothing().when(controlProcessor).publish(targetCapture.capture());
    doNothing().when(controlProcessor)
        .sideProcess(envelopeCapture.capture(), controlCapture.capture());

    IotAccessBase iotAccess = mock(IotAccessBase.class);
    doReturn(mockRegistries).when(iotAccess).getRegistries();
    controlProcessor.iotAccess = iotAccess;

    TargetProcessor targetProcessor = mock(TargetProcessor.class);
    controlProcessor.targetProcessor = targetProcessor;
    doReturn(LAST_SEEN).when(targetProcessor).getLastSeen(eq(TEST_REGISTRY));

    queryHandler = new CloudQueryHandler(controlProcessor, query);
  }
}
