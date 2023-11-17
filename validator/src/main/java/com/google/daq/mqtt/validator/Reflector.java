package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.validator.Validator.REQUIRED_FUNCTION_VER;
import static com.google.udmi.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.mergeObject;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
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

  private static final int RETRY_COUNT = 1;
  private final List<String> reflectCommands;
  private String siteDir;
  private ExecutionConfiguration executionConfiguration;
  private File baseDir;
  private IotReflectorClient client;

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
    try {
      reflector.reflect();
    } finally {
      reflector.shutdown();
    }
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
    int retryCount = RETRY_COUNT;
    String recvId = null;
    String sendId = client.publish(executionConfiguration.device_id, topic, data);
    System.err.println("Waiting for return transaction " + sendId);
    do {
      MessageBundle messageBundle = client.takeNextMessage(QuerySpeed.SHORT);
      if (messageBundle == null) {
        System.err.println("Receive timeout, retries left: " + --retryCount);
        if (retryCount == 0) {
          throw new RuntimeException("Maximum retry count reached");
        }
      } else {
        recvId = messageBundle.attributes.get("transactionId");
      }
    } while (!sendId.equals(recvId));
  }

  private void initialize() {
    if (executionConfiguration.key_file == null) {
      String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
      System.err.println("Using reflector key file " + keyFile);
      executionConfiguration.key_file = keyFile;
    }
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
            executionConfiguration.project_id = removeNextArg(listCopy);
            break;
          case "-s":
            setSiteDir(removeNextArg(listCopy));
            break;
          case "-d":
            executionConfiguration.device_id = removeNextArg(listCopy);
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
    if (executionConfiguration.update_to == null) {
      throw new IllegalArgumentException("No reflect directives specified!");
    }
    return listCopy;
  }

  private void processProfile(File profilePath) {
    executionConfiguration = ConfigUtil.readExeConfig(profilePath);
    ifNotNullThen(executionConfiguration.site_model, this::setSiteDir);
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
      ExecutionConfiguration siteConfig = CloudIotManager.validate(
          ConfigUtil.readExeConfig(cloudConfig), executionConfiguration.project_id);
      executionConfiguration = mergeObject(executionConfiguration, siteConfig);
    }
  }
}
