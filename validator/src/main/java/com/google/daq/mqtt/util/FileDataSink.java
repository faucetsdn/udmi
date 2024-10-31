package com.google.daq.mqtt.util;

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.GeneralUtils;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create a data sink that writes to local files.
 */
public class FileDataSink implements MessagePublisher {

  public static final String VALIDATION_SUFFIX = "out";
  public static final String JSON_SUFFIX = "json";
  public static final String REPORT_JSON_FILENAME = "validation_report.json";
  public static final String REPORT_DEVICE_FMT = "validation_report_%s.json";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
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
  public String publish(String deviceId, String topic, String data) {
    File outFile =
        isValidation(topic) ? getValidationFile(deviceId, data) : getOutputFile(deviceId, topic);
    if (outFile == null) {
      return null;
    }
    try (PrintWriter out = new PrintWriter(Files.newOutputStream(outFile.toPath()))) {
      out.println(data);
    } catch (Exception e) {
      throw new RuntimeException("While writing output file " + outFile.getAbsolutePath(), e);
    }
    return null;
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
    String folderSuffix = (subFolder == null || "update".equals(subFolder)) ? "" : "_" + subFolder;
    return new File(deviceDir, format("%s%s.%s", subType, folderSuffix, suffix));
  }

  private File getDeviceDir(String deviceId) {
    if (deviceId == null || deviceId.isEmpty()) {
      return new File(outBaseDir, "registry");
    } else {
      return new File(outBaseDir, format("devices/%s", deviceId));
    }
  }

  @SuppressWarnings("unchecked")
  private File getValidationFile(String deviceId, String data) {
    try {
      Map<String, Object> message = OBJECT_MAPPER.readValue(data, TreeMap.class);
      String subFolder = (String) message.get("sub_folder");
      String subType = (String) message.get("sub_type");
      boolean isReport = subFolder == null && subType == null;
      boolean isEmpty = !isReport && !message.containsKey("status");
      return isReport ? getReportFile(deviceId)
          : isEmpty ? null : getOutputFile(deviceId, subType, subFolder, VALIDATION_SUFFIX);
    } catch (Exception e) {
      throw new RuntimeException("While processing validation message " + deviceId, e);
    }
  }

  private File getReportFile(String deviceId) {
    return GeneralUtils.ifNotNullGet(deviceId,
        id -> new File(reportFile.getParentFile(), format(REPORT_DEVICE_FMT, id)),
        reportFile);
  }

  @Override
  public void close() {
  }

  @Override
  public String getSubscriptionId() {
    return reportFile.getAbsolutePath();
  }

  @Override
  public void activate() {
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public MessageBundle takeNextMessage(QuerySpeed speed) {
    throw new RuntimeException("Not implemented for file data sink");
  }

}
