package com.google.daq.mqtt.mapping;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.util.Common.removeNextArg;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.util.MessageHandler.HandlerConsumer;
import com.google.daq.mqtt.util.MessageHandler.HandlerSpecification;
import com.google.daq.mqtt.util.PubSubUdmiClient;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.List;

abstract class MappingBase {

  public static final String UDMI_REFLECT = "udmi_reflect";
  private String projectId;
  SiteModel siteModel;
  private PubSubUdmiClient client;
  private String pubsubSubscription;
  private String discoveryNodeId;
  String mappingEngineId;
  private String selfId;

  private void processArgs(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    while (argList.size() > 0) {
      String option = removeNextArg(argList);
      try {
        switch (option) {
          case "-p":
            projectId = removeNextArg(argList);
            break;
          case "-s":
            siteModel = new SiteModel(removeNextArg(argList));
            break;
          case "-t":
            pubsubSubscription = removeNextArg(argList);
            break;
          case "-d":
            discoveryNodeId = removeNextArg(argList);
            break;
          case "-e":
            mappingEngineId = removeNextArg(argList);
            break;
          case "--":
            remainingArgs(argList);
            return;
          default:
            throw new RuntimeException("Unknown cmdline option " + option);
        }
      } catch (Exception e) {
        throw new RuntimeException("While processing option " + option);
      }
    }
  }

  void initialize(String flavor, String[] args, List<HandlerSpecification> handlers) {
    pubsubSubscription = "mapping-" + flavor;
    selfId = "_mapping_" + flavor;
    processArgs(args);
    siteModel.initialize();
    checkNotNull(siteModel, "site model not defined");
    String registryId = checkNotNull(siteModel.getRegistryId(), "site model registry_id null");
    String updateTopic = checkNotNull(siteModel.getUpdateTopic(), "site model update_topic null");
    String projectId = checkNotNull(this.projectId, "project id not defined");
    String subscription = checkNotNull(this.pubsubSubscription, "subscription not defined");
    client = new PubSubUdmiClient(projectId, registryId, subscription, UDMI_REFLECT, false);
    handlers.forEach(this::registerHandler);
  }

  void remainingArgs(List<String> argList) {
    if (!argList.isEmpty()) {
      throw new RuntimeException("Extra args not supported: " + Joiner.on(" ").join(argList));
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void registerHandler(HandlerSpecification handlerSpecification) {
    client.registerHandler((Class<T>) handlerSpecification.getKey(),
        (HandlerConsumer<T>) handlerSpecification.getValue());
  }

  protected void messageLoop() {
    client.messageLoop();
  }

  protected void discoveryPublish(Object message) {
    client.publishMessage(checkNotNull(discoveryNodeId, "discovery node id undefined"), message);
  }

  protected void enginePublish(Object message) {
    client.publishMessage(checkNotNull(mappingEngineId, "mapping engine id undefined"), message);
  }

  protected void publishMessage(String deviceId, Object message) {
    client.publishMessage(deviceId, message);
  }

  protected void publishMessage(Object message) {
    publishMessage(selfId, message);
  }
}
