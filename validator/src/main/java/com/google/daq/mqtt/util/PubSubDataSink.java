package com.google.daq.mqtt.util;

import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import java.util.Map;

/**
 * Enable pushing validation results to a PubSub topic.
 */
public class PubSubDataSink implements DataSink {

  public PubSubDataSink(String projectId, String target) {
  }

  @Override
  public void validationResult(String deviceId, String schemaId, Map<String, String> attributes,
      Object message, ErrorTree errorTree) {

  }
}
