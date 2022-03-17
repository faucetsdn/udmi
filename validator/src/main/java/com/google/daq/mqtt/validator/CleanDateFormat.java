package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Date format that strips out the ms to keep compatibility with string representations that
 * don't include allllllll the seconds. This basically makes .equals() work.
 */
public class CleanDateFormat extends ISO8601DateFormat {

  static Date cleanDate(Date parsedDate) {
    parsedDate.setTime(parsedDate.getTime() - parsedDate.getTime() % 1000);
    return parsedDate;
  }

  public static Date cleanDate() {
    return cleanDate(new Date());
  }

  @Override
  public Date parse(String source) throws ParseException {
    return cleanDate(super.parse(source));
  }

}
