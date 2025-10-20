package com.google.daq.mqtt.external;

import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.registrar.LocalDevice;
import java.util.Set;

public interface ExternalProcessor {

  Set<Class<? extends ExternalProcessor>> PROCESSORS = ImmutableSet.of(DboExternalProcessor.class);

  String getName();

  void process(LocalDevice value);
}
