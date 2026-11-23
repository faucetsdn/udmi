package com.google.daq.mqtt.external;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteDevice;
import com.google.udmi.util.SiteModel;
import java.io.File;
import udmi.schema.BuildingConfigEntity;
import udmi.schema.LinkExternalsModel;

/**
 * Processor to generate externally-linked DBO building config files.
 */
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
  public void process(SiteDevice device) {
    LinkExternalsModel linkExternalsModel = device.getMetadata().externals.get(getName());
    String entityId = requireNonNull(
        catchToNull(() -> device.getMetadata().system.guid), "missing system guid");

    BuildingConfig buildingConfig = new BuildingConfig();
    BuildingConfigEntity buildingConfigEntity = buildingConfig.computeIfAbsent(entityId,
        id -> new BuildingConfigEntity());

    String bacnetName = catchToNull(
        () -> device.getMetadata().localnet.families.get("bacnet").name);
    buildingConfigEntity.code = ofNullable(bacnetName)
        .orElseGet(() -> ofNullable(linkExternalsModel.label).orElse(device.getDeviceId()));
    buildingConfigEntity.type =
        (linkExternalsModel.type != null && !linkExternalsModel.type.isEmpty())
            ? linkExternalsModel.type
            : null;

    File dboOut = new File(device.getOutDir(), DBO_OUT_FILE);
    JsonUtil.writeFile(buildingConfig, dboOut);
  }
}
