package com.google.bos.udmi.monitoring;

import com.google.udmi.util.JsonUtil;
import udmi.schema.Monitoring;
import java.io.PrintStream;

public class LogTailJsonOutput implements LogTailOutput {

  PrintStream printStream;

  LogTailJsonOutput() {
    this.printStream = System.out;
  }

  @Override
  public void emitMetric(Monitoring metric)  {
    printStream.println(JsonUtil.stringify(metric));
  }
}
