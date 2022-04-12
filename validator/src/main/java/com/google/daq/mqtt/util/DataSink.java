package com.google.daq.mqtt.util;

import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import java.util.Map;

public interface DataSink {

  void validationResult(String deviceId, String schemaId, Map<String, String> attributes,
      Object message, ErrorTree errorTree);
}
