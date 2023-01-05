package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.daq.mqtt.util.Common.SUBTYPE_PROPERTY_KEY;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.util.Common;
import com.google.daq.mqtt.util.ValidationException;
import com.google.udmi.util.JsonUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Category;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;

/**
 * Encapsulation of device data for a basic reporting device.
 */
public class ReportingDevice {

  private static final char DETAIL_SEPARATOR_CHAR = ';';
  private static final char DETAIL_REPLACE_CHAR = ',';
  private static final String DETAIL_SEPARATOR = DETAIL_SEPARATOR_CHAR + " ";
  private static final Joiner DETAIL_JOINER = Joiner.on(DETAIL_SEPARATOR);
  private static final long THRESHOLD_SEC = 60 * 60;
  private static final String CATEGORY_MISSING_MESSAGE
      = "instance failed to match exactly one schema (matched 0 out of ";
  private static final String CATEGORY_MISSING_REPLACEMENT
      = "instance entry category not recognized";
  private static Date mockNow;
  private final String deviceId;
  private final List<Entry> entries = new ArrayList<>();
  private final List<Entry> messageEntries = new ArrayList<>();
  private final Map<String, Date> messageMarks = new HashMap<>();
  private Date lastSeen = new Date(0); // Always defined, just start a long time ago!
  private ReportingPointset reportingPointset;
  private Metadata metadata;
  private Set<String> missingPoints;
  private Set<String> extraPoints;

  /**
   * Create device with the given id.
   *
   * @param deviceId device iot id
   */
  public ReportingDevice(String deviceId) {
    this.deviceId = deviceId;
  }

  /**
   * Make a status Entry corresponding to a single exception.
   *
   * @param error    exception to summarize
   * @param category entry category
   * @param detail   entry detail
   * @return Entry summarizing the exception
   */
  private static Entry makeEntry(Exception error, String category, String detail) {
    Entry entry = new Entry();
    entry.message = Common.getExceptionMessage(error);
    entry.detail = detail == null ? getExceptionDetail(error) : detail;
    assertTrue("valid entry category", Category.LEVEL.containsKey(category));
    entry.category = Category.VALIDATION_DEVICE_SCHEMA;
    entry.level = Level.ERROR.value();
    entry.timestamp = getTimestamp();
    return entry;
  }

  private static Date getTimestamp() {
    return mockNow != null ? mockNow : new Date();
  }

  private static String getExceptionDetail(Throwable exception) {
    List<String> messages = new ArrayList<>();
    String previousMessage = null;
    while (exception != null) {
      final String useMessage;
      if (exception instanceof ValidationException) {
        useMessage = validationMessage((ValidationException) exception);
      } else {
        String message = Common.getExceptionMessage(exception);
        String line = Common.getExceptionLine(exception, Validator.class);
        useMessage = message + (line == null ? "" : " @" + line);
      }
      if (previousMessage == null || !previousMessage.equals(useMessage)) {
        messages.add(useMessage);
        previousMessage = useMessage;
      }
      exception = exception.getCause();
    }
    return DETAIL_JOINER.join(messages);
  }

  private static String validationMessage(ValidationException exception) {
    return exception.getAllMessages().stream()
        .map(ReportingDevice::replaceCategoryMissing)
        .collect(Collectors.joining(DETAIL_SEPARATOR));
  }

  private static String replaceCategoryMissing(String message) {
    int index = message.indexOf(CATEGORY_MISSING_MESSAGE);
    return index < 0 ? message : message.substring(0, index) + CATEGORY_MISSING_REPLACEMENT;
  }

  /**
   * Create a single status Entry for this device (which may have multiple internal Entries).
   *
   * @param entries list of Entry to summarize
   * @return status entry
   */
  public static Entry getSummaryEntry(List<Entry> entries) {
    if (entries.isEmpty()) {
      return null;
    }

    if (entries.size() == 1) {
      return entries.get(0);
    }

    Entry entry = new Entry();
    entry.category = Category.VALIDATION_DEVICE_MULTIPLE;
    entry.message = "Multiple validation errors";
    entry.detail = DETAIL_JOINER
        .join(entries.stream()
            .map(ReportingDevice::makeEntrySummary)
            .collect(Collectors.toList()));
    entry.level = entries.stream().map(item -> item.level).max(Integer::compareTo)
        .orElse(Level.ERROR.value());
    entry.timestamp = getTimestamp();
    return entry;
  }

  private static String makeEntrySummary(Entry entry) {
    return entry.message.replace(DETAIL_SEPARATOR_CHAR, DETAIL_REPLACE_CHAR);
  }

  static void setMockNow(Instant now) {
    mockNow = Date.from(now);
  }

  /**
   * Get the device's iot id.
   *
   * @return device's iot id
   */
  public String getDeviceId() {
    return deviceId;
  }

  /**
   * Check if this device has been seen recently (any kind of message).
   *
   * @param now current instant
   * @return {@code true} if this has been seen since threshold
   */
  public boolean seenRecently(Instant now) {
    return lastSeen.after(getThreshold(now));
  }

  /**
   * Check if this device as errors.
   *
   * @return {@code true} if this device has errors
   */
  public boolean hasErrors() {
    return !entries.isEmpty();
  }

  /**
   * Validate a message against specific message-type expectations (outside of base schema).
   *
   * @param message    Message to validate
   * @param timestamp  message timestamp string (rather than pull from typed object)
   * @param attributes message attributes
   */
  public void validateMessageType(Object message, Date timestamp, Map<String, String> attributes) {
    if (reportingPointset == null) {
      return;
    }
    final MetadataDiff metadataDiff;
    if (message instanceof PointsetEvent) {
      metadataDiff = reportingPointset.validateMessage((PointsetEvent) message);
    } else if (message instanceof PointsetState) {
      metadataDiff = reportingPointset.validateMessage((PointsetState) message);
    } else {
      throw new RuntimeException("Unknown message type " + message.getClass().getName());
    }

    missingPoints = metadataDiff.missingPoints;
    if (missingPoints != null && !missingPoints.isEmpty()) {
      addError(pointValidationError("missing points", missingPoints), attributes,
          Category.VALIDATION_DEVICE_CONTENT);
    }

    extraPoints = metadataDiff.extraPoints;
    if (!extraPoints.isEmpty()) {
      addError(pointValidationError("extra points", extraPoints), attributes,
          Category.VALIDATION_DEVICE_CONTENT);
    }
  }

  /**
   * Update the last seen timestamp for this device.
   *
   * @param timestamp timestamp for last seen update
   */
  public void updateLastSeen(Date timestamp) {
    lastSeen = (timestamp != null && timestamp.after(lastSeen)) ? timestamp : lastSeen;
  }

  private Exception pointValidationError(String description, Set<String> points) {
    return new ValidationException(
        String.format("Device has %s: %s", description, Joiner.on(", ").join(points)));
  }

  private void addEntry(Entry entry) {
    // entries collects everything, and is garbage-collected by time
    entries.add(entry);

    // newEntries collects everything on a per-message basis
    messageEntries.add(entry);
  }

  /**
   * Add a validation error to this device.
   *
   * @param error      Exception to add
   * @param attributes attributes of message causing error
   * @param category   error category
   */
  void addError(Exception error, Map<String, String> attributes, String category) {
    String subFolder = attributes.get(SUBFOLDER_PROPERTY_KEY);
    String subType = attributes.get(SUBTYPE_PROPERTY_KEY);
    addError(error, category,
        String.format("%s_%s: %s", subType, subFolder, getExceptionDetail(error)));
  }

  void addError(Exception error, String category, String detail) {
    addEntry(makeEntry(error, category, detail));
  }

  /**
   * Get the error Entries associated with this device.
   *
   * @param now current time for thresholding, or null for everything
   * @return Entry list or errors.
   */
  public List<Entry> getErrors(Date now) {
    if (now == null) {
      return entries;
    }
    return entries.stream()
        .filter(entry -> !entry.timestamp.before(getThreshold(now.toInstant())))
        .collect(Collectors.toList());
  }

  /**
   * Clear all errors for this device.
   */
  public void clearErrors() {
    entries.clear();
  }

  public Metadata getMetadata() {
    return metadata;
  }

  /**
   * Set the metadata record for this device.
   *
   * @param metadata metadata to set
   */
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
    this.reportingPointset = new ReportingPointset(metadata);
  }

  public Set<String> getMissingPoints() {
    return missingPoints;
  }

  public Set<String> getExtraPoints() {
    return extraPoints;
  }

  public void expireEntries(Instant now) {
    entries.removeIf(entry -> entry.timestamp.before(getThreshold(now)));
  }

  private Date getThreshold(Instant now) {
    return Date.from(now.minusSeconds(THRESHOLD_SEC));
  }

  public boolean markMessageType(String schemaName, Instant now) {
    Date previous = messageMarks.put(schemaName, getTimestamp());
    return previous == null || previous.before(getThreshold(now));
  }

  public Date getLastSeen() {
    return lastSeen;
  }

  public void clearMessageEntries() {
    messageEntries.clear();
  }

  public List<Entry> getMessageEntries() {
    return messageEntries;
  }

  /**
   * Encapsulation of metadata differences.
   */
  public static class MetadataDiff {
    public Set<String> extraPoints;
    public Set<String> missingPoints;
  }
}
