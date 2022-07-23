package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.bos.iot.core.proxy.MessagePublisher;
import java.io.File;
import java.util.Map;
import java.util.function.BiConsumer;

public class FileDataSink implements MessagePublisher {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final String REPORT_JSON_FILENAME = "validation_report.json";
  private final File metadataReportFile;

  public FileDataSink(File outBaseDir) {
    metadataReportFile = new File(outBaseDir, REPORT_JSON_FILENAME);
    System.err.println("Generating report file in " + metadataReportFile.getAbsolutePath());
    System.err.println("Writing validation report to " + metadataReportFile.getAbsolutePath());
    metadataReportFile.delete();
  }


  @Override
  public void publish(String deviceId, String topic, String data) {
    // TODO: This should go somewhere!
  }

  @Override
  public void close() {
  }

  @Override
  public String getSubscriptionId() {
    return metadataReportFile.getAbsolutePath();
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public void processMessage(BiConsumer<Map<String, Object>, Map<String, String>> validator) {
    throw new RuntimeException("Not implemented for file data sink");
  }
}
