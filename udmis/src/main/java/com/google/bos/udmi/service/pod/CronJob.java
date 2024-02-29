package com.google.bos.udmi.service.pod;

import static java.util.Objects.requireNonNull;

import udmi.schema.EndpointConfiguration;

/**
 * Simple clas to manage a distributed cron execution environment.
 */
public class CronJob extends ContainerBase {

  private final String jobId;

  public CronJob(EndpointConfiguration config) {
    super(config);
  }

  public static ContainerProvider from(EndpointConfiguration config) {
    return new CronJob(config);
  }

  @Override
  protected void periodicTask() {
    notice("Cron execution");
  }
}
