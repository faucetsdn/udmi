package com.google.daq.mqtt.external;

import static java.util.Objects.requireNonNull;

import com.google.daq.mqtt.registrar.LocalDevice;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import udmi.schema.BuildingConfigEntity;
import udmi.schema.LinkExternalsModel;

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
    LinkExternalsModel linkExternalsModel = localDevice.getMetadata().externals.get(getName());
    String entityId = requireNonNull(linkExternalsModel.entity_id, "missing external entity_id");

    BuildingConfig buildingConfig = new BuildingConfig();
    BuildingConfigEntity buildingConfigEntity = buildingConfig.computeIfAbsent(entityId,
        id -> new BuildingConfigEntity());

    buildingConfigEntity.code = linkExternalsModel.description;
    buildingConfigEntity.type = linkExternalsModel.entity_type;
    buildingConfigEntity.etag = linkExternalsModel.etag;

    File dboOut = new File(localDevice.getOutDir(), DBO_OUT_FILE);
    JsonUtil.writeFile(buildingConfig, dboOut);
  }
}
