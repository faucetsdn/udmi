package com.google.bos.udmi.monitoring;

import com.google.udmi.util.JsonUtil;
import java.io.PrintStream;
import udmi.schema.Monitoring;

/**
 * Class which can output metrics in a JSON format to a Java print stream.
 */
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
