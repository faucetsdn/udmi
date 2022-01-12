package daq.pubber;

public class LoggerFactory {

  public static Logger getLogger(Class<?> clazz) {
    return new Logger();
  }
}