package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.isoConvert;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.pod.ContainerProvider;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.time.Duration;
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
public class CronJob extends ProcessorBase {

  private final Envelope srcEnvelope;
  private final Object message;
  private final SortedMap<String, Date> podReceiveTime = new ConcurrentSkipListMap<>();
  private final Duration waitAndListen;
  private final AtomicReference<Date> previousTick = new AtomicReference<>();

  /**
   * Create an instance with the given config.
   */
  public CronJob(EndpointConfiguration config) {
    super(config);

    distributorName = config.distributor;
    waitAndListen = Duration.ofSeconds(periodicSec / 2);

    String[] targetMessage = config.payload.split(":", 2);

    String[] parts = targetMessage[0].split("/");
    srcEnvelope = new Envelope();
    srcEnvelope.subType = SubType.fromValue(parts[0]);
    srcEnvelope.subFolder = SubFolder.fromValue(parts[1]);
    srcEnvelope.deviceRegistryId = ifTrueGet(parts.length >= 3, () -> parts[3]);
    srcEnvelope.deviceId = ifTrueGet(parts.length >= 4, () -> parts[4]);

    DistributorPipe distributor = UdmiServicePod.maybeGetComponent(distributorName);
    srcEnvelope.gatewayId = distributor.getRouteId(containerId);

    Class<?> messageClass = MessageDispatcherImpl.getMessageClassFor(srcEnvelope, false);
    String payload = ifTrueGet(targetMessage.length > 1, () -> targetMessage[1], EMPTY_JSON);
    message = fromStringStrict(messageClass, payload);

    info("Set-up cron job for %s %s/%s to %s/%s", containerId, srcEnvelope.subType,
        srcEnvelope.subFolder,
        srcEnvelope.deviceRegistryId, srcEnvelope.deviceId);
  }

  public static ContainerProvider from(EndpointConfiguration config) {
    return new CronJob(config);
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
    scheduleIn(waitAndListen, () -> ifTrueThen(iAmGroot(before), this::processGroot));
  }

  private boolean iAmGroot(Date cutoffTime) {
    debug("Check grootness for %s after %s", srcEnvelope.gatewayId, isoConvert(cutoffTime));

    if (cutoffTime == null) {
      return false;
    }

    podReceiveTime.entrySet().removeIf(entry -> entry.getValue().before(cutoffTime));
    debug("Result %s is groot out of %s", podReceiveTime.firstKey(), podReceiveTime.size());
    return srcEnvelope.gatewayId.equals(podReceiveTime.firstKey());
  }

  private void processGroot() {
    warn("I am groot %s, so do cron stuff here", srcEnvelope.gatewayId);
  }

  private void trackPod(Envelope envelope) {
    debug("Pod timestamp update %s to %s", envelope.gatewayId, isoConvert(envelope.publishTime));
    podReceiveTime.put(envelope.gatewayId, envelope.publishTime);
  }
}
