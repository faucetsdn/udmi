package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.udmi.util.JsonUtil;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Date;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageTemplateData;

/**
 * Simple clas to manage a distributed cron execution environment.
 */
public class CronProcessor extends ProcessorBase {

  private static final String PAYLOAD_SEPARATOR = ":";
  private static final String PATH_SEPARATOR = "/";
  private static final long CUTOFF_INTERVALS = 3;
  private static final DefaultMustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();
  private static final SortedMap<String, Instant> TRACKER = new ConcurrentSkipListMap<>();
  private static final String HEARTBEAT_NAME = "heartbeat";
  private static final String HEARTBEAT_SUFFIX = PATH_SEPARATOR + HEARTBEAT_NAME;
  private static Integer HEARTBEAT_SEC;
  private final Envelope srcEnvelope;
  private final Mustache template;
  private final Class<?> messageClass;
  private final MessageTemplateData dataModel = new MessageTemplateData();

  /**
   * Create an instance with the given config.
   */
  public CronProcessor(EndpointConfiguration config) {
    super(config);

    ifTrueThen(HEARTBEAT_NAME.equals(config.name), () -> HEARTBEAT_SEC = config.periodic_sec);

    distributorName = config.distributor;

    if (config.payload == null) {
      srcEnvelope = new Envelope();
      template = MUSTACHE_FACTORY.compile(new StringReader(EMPTY_JSON), "empty template");
      messageClass = Object.class;
    } else {
      String[] targetMessage = config.payload.split(PAYLOAD_SEPARATOR, 2);

      String[] parts = targetMessage[0].split(PATH_SEPARATOR, 4);
      srcEnvelope = new Envelope();
      srcEnvelope.subType = SubType.fromValue(parts[0]);
      srcEnvelope.subFolder = SubFolder.fromValue(parts[1]);
      srcEnvelope.deviceRegistryId = ifTrueGet(parts.length >= 3, () -> parts[3]);
      srcEnvelope.deviceId = ifTrueGet(parts.length >= 4, () -> parts[4]);

      messageClass = MessageDispatcherImpl.getMessageClassFor(srcEnvelope, false);
      String payload = ifTrueGet(targetMessage.length > 1, () -> targetMessage[1], EMPTY_JSON);
      template = MUSTACHE_FACTORY.compile(new StringReader(payload), "payload template");
    }
  }

  private static String getContainerId(Envelope envelope) {
    return envelope.gatewayId.split(PATH_SEPARATOR, 2)[0];
  }

  @Override
  protected void defaultHandler(Object message) {
    trackPod(getContinuation(message).getEnvelope());
  }

  @Override
  protected void periodicTask() {
    ifNotNullThen(srcEnvelope.subType, () ->
        info("Distributing %s %s/%s to %s/%s", containerId, srcEnvelope.subType,
            srcEnvelope.subFolder, srcEnvelope.deviceRegistryId, srcEnvelope.deviceId));

    try {
      Date publishTime = new Date();
      srcEnvelope.publishTime = publishTime;
      dataModel.timestamp = isoConvert(publishTime);

      trackPod(srcEnvelope);

      StringWriter stringWriter = new StringWriter();
      template.execute(stringWriter, dataModel).flush();
      Object message = fromStringStrict(messageClass, stringWriter.toString());

      ifTrueThen(isAmGroot(), () -> processGroot(message));
      distributor.publish(srcEnvelope, message, containerId);
    } catch (Exception e) {
      throw new RuntimeException("While executing cron task", e);
    }
  }

  private boolean isAmGroot() {
    if (srcEnvelope.gatewayId == null || srcEnvelope.subFolder == null) {
      return false;
    }
    debug("Received %s is groot: %s", TRACKER.firstKey(), CSV_JOINER.join(TRACKER.keySet()));
    return getContainerId(srcEnvelope).equals(TRACKER.firstKey());
  }

  private void processGroot(Object message) {
    debug("Publishing as %s: %s", stringifyTerse(srcEnvelope), stringifyTerse(message));
    publish(srcEnvelope, message);
  }

  private void trackPod(Envelope envelope) {
    if (envelope.gatewayId == null || !envelope.gatewayId.endsWith(HEARTBEAT_SUFFIX)) {
      return;
    }
    debug("Pod timestamp update %s to %s", envelope.gatewayId, isoConvert(envelope.publishTime));

    TRACKER.put(getContainerId(envelope), envelope.publishTime.toInstant());
    Instant cutoffTime = Instant.now().minusSeconds(HEARTBEAT_SEC * CUTOFF_INTERVALS);
    TRACKER.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoffTime));
    debug("Received values: " + TRACKER.size() + " " + CSV_JOINER.join(
        TRACKER.values().stream().map(JsonUtil::isoConvert).toList()));
  }

  @Override
  public void activate() {
    super.activate();
    srcEnvelope.gatewayId = distributor.getRouteId(containerId);
    info("Activated cron as %s", stringifyTerse(srcEnvelope));
  }
}
