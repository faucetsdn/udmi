package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.bos.iot.core.proxy.MessagePublisher;
import com.google.daq.mqtt.validator.Validator.MetadataReport;
import java.io.File;

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
  public void validationReport(MetadataReport metadataReport) {
    try {
      OBJECT_MAPPER.writeValue(metadataReportFile, metadataReport);
    } catch (Exception e) {
      throw new RuntimeException(
          "While generating metadata report file " + metadataReportFile.getAbsolutePath(), e);
    }
  }
}
