package com.google.daq.mqtt.external;

import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.SiteDevice;
import java.util.Set;

/**
 * Interface encompassing processing of linked externals.
 */
public interface ExternalProcessor {

  Set<Class<? extends ExternalProcessor>> PROCESSORS = ImmutableSet.of(DboExternalProcessor.class);

  String getName();

  void process(SiteDevice value);
}
