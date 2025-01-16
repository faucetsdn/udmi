package com.google.daq.mqtt.registrar;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;

abstract class Summarizer {

  public static final String UNKNOWN_NUM_ID = "unknown";
  protected File outFile;

  public abstract void summarize(Map<String, LocalDevice> localDevices,
      Map<String, Object> errorSummary, Map<String, CloudModel> extraDevices) throws Exception;

  static class JsonSummarizer extends Summarizer {

    @Override
    public void summarize(Map<String, LocalDevice> localDevices, Map<String, Object> errorSummary,
        Map<String, CloudModel> extraDevices)
        throws Exception {
      OBJECT_MAPPER.writeValue(outFile, errorSummary);
    }
  }

  static class CsvSummarizer extends Summarizer {

    public static final String DEVICE_ID_HEADER = "Device Id";
    public static final String NUM_ID_HEADER = "Device Num Id";
    public static final String STATUS_HEADER = "Status";
    public static final String ACTIVE_HEADER = "Last Active";
    public static final String DETAIL_HEADER = "Detail";
    public static final String NO_DETAIL = "n/a";

    List<String> headers = ImmutableList.of(
        DEVICE_ID_HEADER,
        NUM_ID_HEADER,
        STATUS_HEADER,
        ACTIVE_HEADER,
        DETAIL_HEADER);

    @Override
    public void summarize(Map<String, LocalDevice> localDevices, Map<String, Object> errorSummary,
        Map<String, CloudModel> maybeCloudModels)
        throws Exception {
      Map<String, CloudModel> cloudModels = ofNullable(maybeCloudModels).orElse(ImmutableMap.of());
      Set<String> combined = new TreeSet<>(Sets.union(localDevices.keySet(), cloudModels.keySet()));
      try (PrintWriter writer = new PrintWriter(outFile)) {
        writer.println(CSV_JOINER.join(headers));
        combined.forEach(deviceId -> {
          LocalDevice localDevice = localDevices.get(deviceId);
          CloudModel cloudModel = cloudModels.get(deviceId);
          correlateModels(localDevice, cloudModel);
          Map<String, String> rowValues =
              ofNullable(ifNotNullGet(cloudModel, this::extractDeviceRow))
                  .orElse(ifNotNullGet(localDevice, this::extractDeviceRow));
          List<String> listValues = new ArrayList<>(headers.stream().map(rowValues::get).toList());
          listValues.set(headers.indexOf(DEVICE_ID_HEADER), deviceId);
          writer.println(CSV_JOINER.join(listValues));
        });
      }
    }

    private void correlateModels(LocalDevice localDevice, CloudModel cloudModel) {
      if (localDevice != null && cloudModel != null && localDevice.hasErrors()) {
        cloudModel.operation = Operation.ERROR;
      }
    }

    private Map<String, String> extractDeviceRow(CloudModel cloudModel) {
      return ImmutableMap.of(
          NUM_ID_HEADER, ofNullable(cloudModel.num_id).orElse(UNKNOWN_NUM_ID),
          STATUS_HEADER, ofNullable(cloudModel.operation).orElse(Operation.READ).toString(),
          ACTIVE_HEADER, JsonUtil.isoConvert(cloudModel.last_event_time),
          DETAIL_HEADER, ofNullable(cloudModel.detail).orElse(NO_DETAIL));
    }

    private Map<String, String> extractDeviceRow(LocalDevice localDevice) {
      return ImmutableMap.of(
          NUM_ID_HEADER, ofNullable(localDevice.getDeviceNumIdRaw()).orElse(UNKNOWN_NUM_ID),
          STATUS_HEADER, localDevice.getStatus().toString(),
          ACTIVE_HEADER, localDevice.getLastActive(),
          DETAIL_HEADER, NO_DETAIL);
    }
  }
}
