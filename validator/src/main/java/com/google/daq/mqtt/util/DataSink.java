package com.google.daq.mqtt.util;

import com.google.daq.mqtt.validator.ReportingDevice;
import com.google.daq.mqtt.validator.Validator.MetadataReport;
import java.util.Map;

/**
 * Interface for handling validation results (saving to a database).
 */
public interface DataSink {

  /**
   * Process/store a given validation result.
   *
   * @param attributes      message attributes
   * @param message         message that was validated
   * @param reportingDevice validation errors (or null if successful)
   */
  void validationResult(Map<String, String> attributes, Object message,
      ReportingDevice reportingDevice);

  void validationReport(MetadataReport metadataReport);
}
