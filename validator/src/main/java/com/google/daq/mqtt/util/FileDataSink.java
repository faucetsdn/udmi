package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Create a data sink that writes to local files.
 */
public class FileDataSink implements MessagePublisher {

  public static final String VALIDATION_SUFFIX = "out";
  public static final String JSON_SUFFIX = "json";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final String REPORT_JSON_FILENAME = "validation_report.json";
  private final File reportFile;
  private final File outBaseDir;

  /**
   * New instance.
   *
   * @param outBaseDir directory root for output files
   */
  public FileDataSink(File outBaseDir) {
    this.outBaseDir = outBaseDir;
    reportFile = new File(outBaseDir, REPORT_JSON_FILENAME);
    System.err.println("Generating report file in " + reportFile.getAbsolutePath());
    reportFile.delete();
  }

  @Override
  public void publish(String deviceId, String topic, String data) {
    File outFile =
        isValidation(topic) ? getValidationFile(deviceId, data) : getOutputFile(deviceId, topic);
    if (outFile == null) {
      return;
    }
    try (PrintWriter out = new PrintWriter(new FileOutputStream(outFile))) {
      out.println(data);
    } catch (Exception e) {
      throw new RuntimeException("While writing output file " + outFile.getAbsolutePath(), e);
    }
  }

  private boolean isValidation(String topic) {
    return topic.startsWith("validation/");
  }

  private File getOutputFile(String deviceId, String topic) {
    String[] parts = topic.split("/");
    return getOutputFile(deviceId, parts[1], parts[0], JSON_SUFFIX);
  }

  private File getOutputFile(String deviceId, String subType, String subFolder, String suffix) {
    File deviceDir = getDeviceDir(deviceId);
    deviceDir.mkdirs();
    return new File(deviceDir, String.format("%s%s.%s", subType,
            ("update".equals(subFolder) ? "" : "_" + subFolder), suffix));
  }

  private File getDeviceDir(String deviceId) {
    return new File(outBaseDir, String.format("devices/%s", deviceId));
  }

  @SuppressWarnings("unchecked")
  private File getValidationFile(String deviceId, String data) {
    try {
      Map<String, Object> message = OBJECT_MAPPER.readValue(data, TreeMap.class);
      String subFolder = (String) message.get("sub_folder");
      String subType = (String) message.get("sub_type");
      boolean isReport = subFolder == null && subType == null;
      boolean isEmpty = !isReport && !message.containsKey("status");
      return isReport ? reportFile
          : isEmpty ? null : getOutputFile(deviceId, subType, subFolder, VALIDATION_SUFFIX);
    } catch (Exception e) {
      throw new RuntimeException("While processing validation message " + deviceId, e);
    }
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
