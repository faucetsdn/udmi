package com.google.bos.udmi.monitoring;

import java.io.IOException;
import udmi.schema.Monitoring;

/**
 * Defines an interface for output of metrics from LogTail.
 */
public interface LogTailOutput {
  public void emitMetric(Monitoring metric) throws IOException;
}
