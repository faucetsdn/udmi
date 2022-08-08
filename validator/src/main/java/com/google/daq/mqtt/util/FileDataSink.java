package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.bos.iot.core.proxy.MessagePublisher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Create a data sink that writes to local files.
 */
public class FileDataSink implements MessagePublisher {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final String REPORT_JSON_FILENAME = "validation_report.json";
  private final File reportFile;
  private final String validationSrc;
  private final File outBaseDir;

  /**
   * New instance.
   *
   * @param outBaseDir    directory root for output files
   * @param validationSrc source of validation messages
   */
  public FileDataSink(File outBaseDir, String validationSrc) {
    this.validationSrc = validationSrc;
    this.outBaseDir = outBaseDir;
    reportFile = new File(outBaseDir, REPORT_JSON_FILENAME);
    System.err.println("Generating report file in " + reportFile.getAbsolutePath());
    reportFile.delete();
  }

  @Override
  public void publish(String deviceId, String topic, String data) {
    File outFile = getOutputFile(deviceId, topic);
    try (PrintWriter out = new PrintWriter(new FileOutputStream(outFile))) {
      out.println(data);
    } catch (Exception e) {
      throw new RuntimeException("While writing output file " + outFile.getAbsolutePath(), e);
    }
  }

  private File getOutputFile(String deviceId, String topic) {
    return deviceId.equals(validationSrc) ? reportFile : getDeviceFile(deviceId, topic);
  }

  private File getDeviceFile(String deviceId, String topic) {
    File deviceDir = new File(outBaseDir, String.format("devices/%s", deviceId));
    deviceDir.mkdirs();
    String[] parts = topic.split("/");
    return new File(deviceDir, String.format("%s_%s.json", parts[1], parts[0]));
  }

  @Override
  public void close() {
  }

  @Override
  public String getSubscriptionId() {
    return reportFile.getAbsolutePath();
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
