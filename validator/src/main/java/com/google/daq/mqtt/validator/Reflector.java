package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.daq.mqtt.util.Common.NO_SITE;
import static com.google.daq.mqtt.util.Common.removeNextArg;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.FileDataSink;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Reflector {

  private String projectId;
  private String siteDir;
  private CloudIotConfig cloudIotConfig;
  private File baseDir;
  private IotReflectorClient client;

  public Reflector(List<String> argsList) {
    parseArgs(argsList);
  }

  public static void main(String[] args) {
    Reflector reflector = new Reflector(Arrays.asList(args));
    reflector.initialize();
    reflector.reflect();
  }

  private void reflect() {
    client.publish();
  }

  private void initialize() {
    String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    System.err.println("Loading reflector key file from " + keyFile);
    client = new IotReflectorClient(projectId, cloudIotConfig, keyFile);
  }

  private List<String> parseArgs(List<String> argsList) {
    List<String> listCopy = new ArrayList<>(argsList);
    while (!listCopy.isEmpty()) {
      String option = removeNextArg(listCopy);
      try {
        switch (option) {
          case "-p":
            projectId = removeNextArg(listCopy);
            break;
          case "-s":
            setSiteDir(removeNextArg(listCopy));
            break;
        }
      } catch (Exception e) {
        throw new RuntimeException("While processing option " + option, e);
      }
    }
    return listCopy;
  }

  /**
   * Set the site directory to use for this run.
   *
   * @param siteDir site model directory
   */
  public void setSiteDir(String siteDir) {
    if (NO_SITE.equals(siteDir)) {
      siteDir = null;
      baseDir = new File(".");
    } else {
      this.siteDir = siteDir;
      baseDir = new File(siteDir);
      File cloudConfig = new File(siteDir, "cloud_iot_config.json");
      cloudIotConfig = CloudIotManager.validate(ConfigUtil.readCloudIotConfig(cloudConfig),
          projectId);
    }
  }
}
