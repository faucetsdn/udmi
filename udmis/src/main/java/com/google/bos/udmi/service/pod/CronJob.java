package com.google.bos.udmi.service.pod;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;

import com.google.bos.udmi.service.core.ProcessorBase;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Simple clas to manage a distributed cron execution environment.
 */
public class CronJob extends ProcessorBase {

  private final String[] parts;
  private final String payload;

  public CronJob(EndpointConfiguration config) {
    super(config);
    parts = config.payload.split("/", 3);
    info("Set-up cron job for %s/%s", parts[0], parts[1]);
    checkState(parts.length >= 2, "Missing requisite parts from payload " + config.payload);
    payload = ifTrueGet(parts.length > 2, () -> parts[2], null);
  }

  public static ContainerProvider from(EndpointConfiguration config) {
    return new CronJob(config);
  }

  @Override
  protected void periodicTask() {
    Envelope envelope;
    distributor.distribute(envelope, payload);
    publish(payload);
  }
}
