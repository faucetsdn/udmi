package com.google.udmi.util;

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

/**
 * Date format that strips out the ms to keep compatibility with string representations that don't
 * include allllllll the seconds. This basically makes .equals() work.
 */
public class CleanDateFormat extends ISO8601DateFormat {

  /**
   * Clean a date object (remove ms).
   *
   * @param parsedDate date to clean
   * @return cleaned date
   */
  public static Date cleanDate(Date parsedDate) {
    if (parsedDate != null) {
      parsedDate.setTime(parsedDate.getTime() - parsedDate.getTime() % 1000);
    }
    return parsedDate;
  }

  public static Date cleanDate() {
    return cleanDate(new Date());
  }

  /**
   * Clean a date object (remove ms).
   *
   * @param instant Instant to clean
   * @return cleaned date
   */
  public static Instant clean(Instant instant) {
    // Recycle just the seconds, ignoring <1s bits.
    return instant == null ? null : Instant.ofEpochSecond(instant.getEpochSecond());
  }

  /**
   * Check if two dates are equal after cleaning them up.
   *
   * @param dateBase   base date
   * @param dateTarget compare against
   * @return if the dates are equal (after cleaning)
   */
  public static boolean dateEquals(Date dateBase, Date dateTarget) {
    if (dateBase == null && dateTarget == null) {
      return true;
    }
    if (dateBase == null || dateTarget == null) {
      return false;
    }
    return cleanDate(dateBase).equals(cleanDate(dateTarget));
  }

  @Override
  public Date parse(String source) throws ParseException {
    return cleanDate(super.parse(source));
  }

}
