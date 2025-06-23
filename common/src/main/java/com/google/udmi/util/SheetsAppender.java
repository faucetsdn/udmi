package com.google.udmi.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;

public class SheetsAppender extends AppenderBase<ILoggingEvent> {

  private static SheetsOutputStream sheetsOutputStream;
  private Encoder<ILoggingEvent> encoder;

  @Override
  protected void append(ILoggingEvent eventObject) {
    if (sheetsOutputStream == null) {
      return;
    }
    try {
      byte[] formattedLog = this.encoder.encode(eventObject);
      sheetsOutputStream.write(formattedLog);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void setSheetsOutputStream(SheetsOutputStream stream) {
    sheetsOutputStream = stream;
  }

  public Encoder<ILoggingEvent> getEncoder() {
    return encoder;
  }

  public void setEncoder(Encoder<ILoggingEvent> encoder) {
    this.encoder = encoder;
  }
}