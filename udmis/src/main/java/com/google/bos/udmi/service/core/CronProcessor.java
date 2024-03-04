package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.udmi.util.JsonUtil;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Simple clas to manage a distributed cron execution environment.
 */
public class CronProcessor extends ProcessorBase {

  public static final String PAYLOAD_SEPARATOR = ":";
  public static final String PATH_SEPARATOR = "/";
  private final Envelope srcEnvelope;
  private final Object message;
  private final SortedMap<String, Date> received = new ConcurrentSkipListMap<>();
  private final Duration waitAndListen;
  private final AtomicReference<Date> previousTick = new AtomicReference<>();

  /**
   * Create an instance with the given config.
   */
  public CronProcessor(EndpointConfiguration config) {
    super(config);

    distributorName = config.distributor;
    waitAndListen = Duration.ofSeconds(periodicSec / 2);

    String[] targetMessage = config.payload.split(PAYLOAD_SEPARATOR, 2);

    String[] parts = targetMessage[0].split(PATH_SEPARATOR, 4);
    srcEnvelope = new Envelope();
    srcEnvelope.subType = SubType.fromValue(parts[0]);
    srcEnvelope.subFolder = SubFolder.fromValue(parts[1]);
    srcEnvelope.deviceRegistryId = ifTrueGet(parts.length >= 3, () -> parts[3]);
    srcEnvelope.deviceId = ifTrueGet(parts.length >= 4, () -> parts[4]);

    Class<?> messageClass = MessageDispatcherImpl.getMessageClassFor(srcEnvelope, false);
    String payload = ifTrueGet(targetMessage.length > 1, () -> targetMessage[1], EMPTY_JSON);
    message = fromStringStrict(messageClass, payload);
  }

  @Override
  protected void defaultHandler(Object message) {
    trackPod(getContinuation(message).getEnvelope());
  }

  @Override
  protected void periodicTask() {
    info("Distributing %s %s/%s to %s/%s", containerId, srcEnvelope.subType, srcEnvelope.subFolder,
        srcEnvelope.deviceRegistryId, srcEnvelope.deviceId);

    Date publishTime = new Date();
    srcEnvelope.publishTime = publishTime;

    distributor.publish(srcEnvelope, message, containerId);

    trackPod(srcEnvelope);

    Date before = previousTick.getAndSet(publishTime);

    // Wait for half the window, to make sure everybody has a chance to report in.
    scheduleIn(waitAndListen, () -> ifTrueThen(isAmGroot(before), this::processGroot));
  }

  private boolean isAmGroot(Date cutoffTime) {
    debug("Check grootness for %s after %s", srcEnvelope.gatewayId, isoConvert(cutoffTime));

    if (cutoffTime == null) {
      return false;
    }

    debug("Received values: " + received.size() + " " + CSV_JOINER.join(
        received.values().stream().map(JsonUtil::isoConvert).toList()));
    received.entrySet().removeIf(entry -> entry.getValue().before(cutoffTime));
    debug("Received %s is groot: %s", received.firstKey(), CSV_JOINER.join(received.keySet()));
    return srcEnvelope.gatewayId.equals(received.firstKey());
  }

  private void processGroot() {
    debug("Publishing as %s: %s", stringifyTerse(srcEnvelope), stringifyTerse(message));
    publish(srcEnvelope, message);
  }

  private void trackPod(Envelope envelope) {
    debug("Pod timestamp update %s to %s", envelope.gatewayId, isoConvert(envelope.publishTime));
    received.put(envelope.gatewayId, envelope.publishTime);
    debug("Received values: " + received.size() + " " + CSV_JOINER.join(
        received.values().stream().map(JsonUtil::isoConvert).toList()));
  }

  @Override
  public void activate() {
    super.activate();
    srcEnvelope.gatewayId = distributor.getRouteId(containerId);
    info("Activated cron as %s", stringifyTerse(srcEnvelope));
  }
}
