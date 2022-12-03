package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.daq.mqtt.util.Common.NO_SITE;
import static com.google.daq.mqtt.util.Common.removeNextArg;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
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
    try {
      Thread.sleep(5000);
    } catch (Exception e) {
      throw new RuntimeException("While sleeping", e);
    }
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
    client.publish(deviceId, topic, data);
  }

  private void initialize() {
    String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    System.err.println("Loading reflector key file from " + keyFile);
    executionConfiguration.key_file = keyFile;
    client = new IotReflectorClient(executionConfiguration);
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
          case "-d":
            deviceId = removeNextArg(listCopy);
            break;
          default:
            listCopy.add(option);
            return listCopy; // default case is the remaining list of reflection directives
        }
      } catch (Exception e) {
        throw new RuntimeException("While processing option " + option, e);
      }
    }
    throw new IllegalArgumentException("No reflect directives specified!");
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
