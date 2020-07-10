package daq.udmi;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Date;

public class Entry {
  public String message;
  public String detail;
  public String category = "com.acme.pubber";
  public Integer level = 500;
  public Date timestamp = new Date();

  public Entry(String message) {
    this.message = message;
  }

  public Entry(Exception e) {
    message = e.toString();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    e.printStackTrace(new PrintStream(outputStream));
    detail = outputStream.toString();
    category = e.getStackTrace()[0].getClassName();
    level = 800;
  }
}
