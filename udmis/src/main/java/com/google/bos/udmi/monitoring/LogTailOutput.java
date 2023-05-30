package com.google.bos.udmi.monitoring;

import java.io.IOException;
import udmi.schema.Monitoring;

public interface LogTailOutput {
  public void emitMetric(Monitoring metric) throws IOException;
}
