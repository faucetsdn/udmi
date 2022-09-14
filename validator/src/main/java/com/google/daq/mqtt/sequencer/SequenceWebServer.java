package com.google.daq.mqtt.sequencer;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.util.Common;
import com.google.daq.mqtt.util.SimpleWebServer;
import com.google.daq.mqtt.util.ValidatorConfig;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.Device;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import udmi.schema.Level;

public class SequenceWebServer extends SimpleWebServer {

  public static final Joiner JOINER = Joiner.on(", ");
  private String projectId;

  public SequenceWebServer(List<String> args) {
    super(args);
    processArgs(args);
    setHandler(this::handle);
  }

  public static void main(String[] args) {
    List<String> argList = new ArrayList<>(Arrays.asList(args));
    new SequenceWebServer(argList);
    if (!argList.isEmpty()) {
      throw new IllegalStateException("Extra unexpected args: " + JOINER.join(argList));
    }
  }

  private void processArgs(List<String> remaining) {
    if (remaining.isEmpty()) {
      throw new IllegalArgumentException("Missing project_id argument");
    }
    projectId = remaining.remove(0);
  }

  private void handle(Map<String, String> params) {
    String siteModel = params.remove("site_model");
    if (params.isEmpty()) {
      throw new IllegalArgumentException("Unexpected arguments: " + JOINER.join(params.keySet()));
    }
    processSiteModel(siteModel);
  }

  void processSiteModel(String sitePath) {
    SiteModel siteModel = new SiteModel(sitePath);
    siteModel.initialize();
    siteModel.forEachDevice(device -> processDevice(siteModel, device));
  }

  private void processDevice(SiteModel siteModel, Device device) {
    String deviceId = device.deviceId;
    ValidatorConfig config = new ValidatorConfig();
    config.project_id = projectId;
    config.site_model = siteModel.getSitePath();
    config.device_id = deviceId;
    config.key_file = siteModel.validatorKey();
    config.udmi_version = Common.getUdmiVersion();
    config.serial_no = SequenceRunner.SERIAL_NO_MISSING;
    config.log_level = Level.INFO.name();
    SequenceTestRunner.process(config);
  }


}
