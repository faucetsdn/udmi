package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.stringifyTerse;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.pod.ContainerProvider;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Simple clas to manage a distributed cron execution environment.
 */
public class CronJob extends ProcessorBase {

  private static final String SELF_ID = "groot";
  private final Envelope envelope;
  private final Object message;

  /**
   * Create an instance with the given config.
   */
  public CronJob(EndpointConfiguration config) {
    super(config);

    distributorName = config.distributor;

    String[] targetMessage = config.payload.split(":", 2);

    String[] parts = targetMessage[0].split("/");
    envelope = new Envelope();
    envelope.subType = SubType.fromValue(parts[0]);
    envelope.subFolder = SubFolder.fromValue(parts[1]);
    envelope.deviceRegistryId = ifTrueGet(parts.length >= 3, () -> parts[3]);
    envelope.deviceId = ifTrueGet(parts.length >= 4, () -> parts[4]);
    envelope.gatewayId = SELF_ID + DistributorPipe.ROUTE_SEPERATOR + config.name;

    Class<?> messageClass = MessageDispatcherImpl.getMessageClassFor(envelope, false);
    String payload = ifTrueGet(targetMessage.length > 1, () -> targetMessage[1], EMPTY_JSON);
    message = fromStringStrict(messageClass, payload);

    info("Set-up cron job for %s %s/%s to %s/%s", containerId, envelope.subType, envelope.subFolder,
        envelope.deviceRegistryId, envelope.deviceId);
  }

  public static ContainerProvider from(EndpointConfiguration config) {
    return new CronJob(config);
  }

  @Override
  protected void periodicTask() {
    info("Distributing %s %s/%s to %s/%s", containerId, envelope.subType, envelope.subFolder,
        envelope.deviceRegistryId, envelope.deviceId);
    distributor.publish(envelope, message, containerId);
    handleTick(envelope, message);
  }

  @Override
  protected void defaultHandler(Object message) {
    handleTick(getContinuation(message).getEnvelope(), message);
  }

  private void handleTick(Envelope envelope, Object message) {
    debug("Cron Job update " + stringifyTerse(envelope) + " " + stringifyTerse(message));
  }
}
