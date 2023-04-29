package com.google.bos.udmi.service.access;

import static com.google.udmi.util.JsonUtil.convertTo;

import com.google.common.base.Preconditions;
import com.google.udmi.util.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import udmi.schema.CloudModel;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess;
import udmi.schema.UdmiConfig;

/**
 * Access provider for testing purposes.
 */
public class MockIotAccessProvider implements IotAccessProvider {

  public final List<Object> captured = new ArrayList<>();
  private final Consumer<Object> consumer;

  public MockIotAccessProvider(IotAccess iotAccess) {
    consumer = captured::add;
  }

  public MockIotAccessProvider(Consumer<Object> consumer) {
    this.consumer = consumer;
  }

  @Override
  public void activate() {
  }

  @Override
  public void modifyConfig(String registryId, String deviceId, SubFolder udmi, String contents) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void updateConfig(String registryId, String deviceId, String config) {
    Map<String, Object> configMap = JsonUtil.toMap(config);
    consumer.accept(convertTo(UdmiConfig.class, configMap.remove(SubFolder.UDMI.value())));
    Preconditions.checkState(configMap.size() == 0, "residual entries");
  }

  @Override
  public void shutdown() {
  }

  @Override
  public CloudModel listRegistryDevices(String deviceRegistryId) {
    throw new RuntimeException("Not yet implemented");
  }
}
