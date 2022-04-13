package com.google.daq.mqtt.util;

import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import java.util.Map;

/**
 * Interface for handling validation results (saving to a database).
 */
public interface DataSink {

  /**
   * Process/store a given validation result.
   *
   * @param deviceId   device that was validated
   * @param schemaId   schema against which it was validated
   * @param attributes message attributes
   * @param message    message that was validated
   * @param errorTree  validation errors (or null if successful)
   */
  void validationResult(String deviceId, String schemaId, Map<String, String> attributes,
      Object message, ErrorTree errorTree);
}
