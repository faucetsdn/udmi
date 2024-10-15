package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.validator.Validator.TOOLS_FUNCTIONS_VERSION;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.String.format;

import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.Common;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;

/**
 * General utility for working with UDMI Reflector messages.
 */
public class Reflector {

  private static final int RETRY_COUNT = 2;
  public static final String REFLECTOR_TOOL_NAME = "reflector";
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
    Instant startTime = Instant.now();
    Instant endTime = startTime.plusSeconds(QuerySpeed.SHORT.seconds());
    try {
      MessageBundle messageBundle;

      do {
        if (endTime.isBefore(Instant.now())) {
          throw new RuntimeException("Timeout waiting for reflector response");
        }
        messageBundle = client.takeNextMessage(QuerySpeed.SHORT);
        if (messageBundle == null) {
          System.err.println("Receive timeout, retries left: " + --retryCount);
          if (retryCount == 0) {
            throw new RuntimeException("Maximum retry count reached");
          }
        } else {
          recvId = messageBundle.attributes.get("transactionId");
        }
      } while (!sendId.equals(recvId));

      if (SubFolder.ERROR.value().equals(messageBundle.attributes.get(SUBFOLDER_PROPERTY_KEY))) {
        throw new RuntimeException(
            "Error processing request: " + messageBundle.message.get(ERROR_KEY));
      }
    } catch (Exception e) {
      throw new RuntimeException(format("While waiting for return transaction %s started %s",
          sendId, isoConvert(startTime)), e);
    }
  }

  private void initialize() {
    if (executionConfiguration.key_file == null) {
      String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
      System.err.println("Using reflector key file " + keyFile);
      executionConfiguration.key_file = keyFile;
    }
    executionConfiguration.udmi_version = Common.getUdmiVersion();
    client = new IotReflectorClient(executionConfiguration, TOOLS_FUNCTIONS_VERSION,
        REFLECTOR_TOOL_NAME);
    client.activate();
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

      // These parameters should always be taken from the site_model.
      executionConfiguration.registry_id = siteConfig.registry_id;
      executionConfiguration.cloud_region = siteConfig.cloud_region;
      executionConfiguration.site_name = siteConfig.site_name;

      // Only use the site_model values for project_spec if not otherwise specified.
      if (executionConfiguration.project_id == null) {
        executionConfiguration.iot_provider = siteConfig.iot_provider;
        executionConfiguration.project_id = siteConfig.project_id;
        executionConfiguration.udmi_namespace = siteConfig.udmi_namespace;
      }
    }
  }
}
