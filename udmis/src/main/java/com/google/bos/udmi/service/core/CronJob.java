package com.google.bos.udmi.service.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.JsonUtil.fromStringStrict;

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

  private final Envelope envelope;
  private final Object message;

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
    envelope.source = config.name;

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
    distributor.distribute(envelope, message);
  }
}

