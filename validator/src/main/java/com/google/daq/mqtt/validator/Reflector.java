package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.validator.Validator.REQUIRED_FUNCTION_VER;
import static com.google.udmi.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.removeNextArg;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.Common;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import udmi.schema.ExecutionConfiguration;

/**
 * General utility for working with UDMI Reflector messages.
 */
public class Reflector {

  private final List<String> reflectCommands;
  private String projectId;
  private String siteDir;
  private ExecutionConfiguration executionConfiguration;
  private File baseDir;
  private IotReflectorClient client;
  private String deviceId;
  private String registrySuffix;

  /**
   * Create an instance of the Reflector class.
   *
   * @param argsList command-line arguments
   */
  public Reflector(List<String> argsList) {
    reflectCommands = parseArgs(argsList);
  }

  /**
   * Let's go.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    Reflector reflector = new Reflector(Arrays.asList(args));
    reflector.initialize();
    reflector.reflect();
    reflector.shutdown();
  }

  private void shutdown() {
    client.close();
  }

  private void reflect() {
    System.err.printf("Reflecting %d directives...%n", reflectCommands.size());
    while (!reflectCommands.isEmpty()) {
      reflect(reflectCommands.remove(0));
    }
    System.err.println("Done with all reflective directives!");
  }

  private void reflect(String directive) {
    System.err.printf("Reflecting %s%n", directive);
    String[] parts = directive.split(":", 2);
    if (parts.length != 2) {
      throw new RuntimeException("Expected topic:file format reflect directive " + directive);
    }
    File file = new File(parts[1]);
    reflect(parts[0], file);
  }

  private void reflect(String topic, File dataFile) {
    try (FileInputStream fis = new FileInputStream(dataFile)) {
      String data = new String(fis.readAllBytes());
      reflect(topic, data);
    } catch (Exception e) {
      throw new RuntimeException("While processing input file " + dataFile.getAbsolutePath(), e);
    }
  }

  private void reflect(String topic, String data) {
    String sendId = client.publish(deviceId, topic, data);
    String recvId;
    System.err.println("Waiting for return transaction " + sendId);
    do {
      MessageBundle messageBundle = client.takeNextMessage();
      recvId = messageBundle.attributes.get("transactionId");
    } while (!sendId.equals(recvId));
  }

  private void initialize() {
    String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    System.err.println("Loading reflector key file from " + keyFile);
    executionConfiguration.key_file = keyFile;
    executionConfiguration.project_id = projectId;
    executionConfiguration.registry_suffix = registrySuffix;
    executionConfiguration.udmi_version = Common.getUdmiVersion();
    client = new IotReflectorClient(executionConfiguration, REQUIRED_FUNCTION_VER);
  }

  private List<String> parseArgs(List<String> argsList) {
    List<String> listCopy = new ArrayList<>(argsList);
    if (!listCopy.isEmpty() && !listCopy.get(0).startsWith("-")) {
      processProfile(new File(listCopy.remove(0)));
    }
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
          case "-d":
            deviceId = removeNextArg(listCopy);
            break;
          default:
            // Restore removed arg, and return remainder of the list and quit parsing.
            listCopy.add(0, option);
            return listCopy;
        }
      } catch (Exception e) {
        throw new RuntimeException("While processing option " + option, e);
      }
    }
    throw new IllegalArgumentException("No reflect directives specified!");
  }

  private void processProfile(File profilePath) {
    ExecutionConfiguration config = ConfigUtil.readExecutionConfiguration(profilePath);
    projectId = config.project_id;
    deviceId = config.device_id;
    registrySuffix = config.registry_suffix;
    setSiteDir(config.site_model);
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
      executionConfiguration = CloudIotManager.validate(
          ConfigUtil.readExecutionConfiguration(cloudConfig),
          projectId);
    }
  }
}
