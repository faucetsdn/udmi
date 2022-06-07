package daq.pubber;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pubber configuration options which change default behavior.
 */
public class ConfigurationOptions {
  public Boolean noHardware = false;
  public Boolean extraPoint = false;
  public Boolean extraField = false;

}
