package com.google.daq.mqtt.util.providers;

/**
 * Provider for the Modbus localnet family.
 */
public class ModbusFamilyProvider implements FamilyProvider {

  @Override
  public String familyKey() {
    return "modbus";
  }

  @Override
  public void validateAddr(String scanAddr) {
    // Basic validation, e.g., must be a number if we strictly followed modbus
    // But we just let it pass for now as it's a test provider.
  }
}
