package daq.pubber;

public class Logger {

  public void error(String message, Exception e) {
    System.err.println(message);
    e.printStackTrace();
  }

  public void error(String forcing_termination) {
  }

  public void warn(String s) {
  }

  public void warn(String mqtt_connection_lost, Throwable cause) {
  }

  public void info(String done_with_main) {
  }

  public void debug(String s) {
  }

}