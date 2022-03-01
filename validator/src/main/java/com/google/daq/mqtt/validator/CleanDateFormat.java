package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;

public class CleanDateFormat extends ISO8601DateFormat {

  @Override
  public Date parse(String source) throws ParseException {
    // JSON dates doesn't typically include ms, so strip them out to make equals() sane.
    return cleanDate(super.parse(source));
  }

  static Date cleanDate(Date parsedDate) {
    parsedDate.setTime(parsedDate.getTime() - parsedDate.getTime() % 1000);
    return parsedDate;
  }

  static Date cleanDate() {
    return cleanDate(new Date());
  }

}
