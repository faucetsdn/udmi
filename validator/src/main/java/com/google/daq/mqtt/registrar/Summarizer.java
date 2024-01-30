package com.google.daq.mqtt.registrar;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

abstract class Summarizer {

  public static final String UNKNOWN_NUM_ID = "unknown";
  protected File outFile;

  public abstract void summarize(Map<String, LocalDevice> localDevices,
      Map<String, Object> errorSummary) throws Exception;

  static class JsonSummarizer extends Summarizer {

    @Override
    public void summarize(Map<String, LocalDevice> localDevices, Map<String, Object> errorSummary)
        throws Exception {
      OBJECT_MAPPER.writeValue(outFile, errorSummary);
    }
  }

  static class CsvSummarizer extends Summarizer {

    List<String> headers = ImmutableList.of(
        "Device Id",
        "Device Num Id",
        "Status",
        "Last Active");

    @Override
    public void summarize(Map<String, LocalDevice> localDevices, Map<String, Object> errorSummary)
        throws Exception {
      try (PrintWriter writer = new PrintWriter(outFile)) {
        writer.println(CSV_JOINER.join(headers));
        localDevices.values().forEach(device -> {
          List<String> values = ImmutableList.of(
              device.getDeviceId(),
              ofNullable(device.getDeviceNumIdRaw()).orElse(UNKNOWN_NUM_ID),
              device.getStatus().toString(),
              device.getLastActive());
          writer.println(CSV_JOINER.join(values));
        });
      }
    }
  }
}
