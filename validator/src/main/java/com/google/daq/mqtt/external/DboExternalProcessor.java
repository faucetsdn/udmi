package com.google.daq.mqtt.external;

import com.google.daq.mqtt.registrar.LocalDevice;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;

public class DboExternalProcessor implements ExternalProcessor {

  private static final String DBO_OUT_FILE = "building_config.json";
  private final SiteModel siteModel;

  public DboExternalProcessor(SiteModel siteModel) {
    this.siteModel = siteModel;
  }

  @Override
  public String getName() {
    return "dbo";
  }

  @Override
  public void process(LocalDevice localDevice) {
    File dboOut = new File(localDevice.getOutDir(), DBO_OUT_FILE);
    BuildingConfig buildingConfig = new BuildingConfig();
    JsonUtil.writeFile(buildingConfig, dboOut);
  }
}
