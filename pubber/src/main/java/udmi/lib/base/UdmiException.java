package udmi.lib.base;

/**
 * Container for UDMI-specific exceptions.
 */
public class UdmiException extends RuntimeException {
  private final String category;

  /**
   * Creates a new UdmiException with a specific category and message.
   *
   * @param category The associated logging category.
   * @param message  The detail message.
   */
  public UdmiException(String category, String message) {
    super(message);
    this.category = category;
  }

  /**
   * Creates a new UdmiException with a specific category, message, and cause.
   *
   * @param category The associated logging category.
   * @param message  The detail message.
   * @param cause    The cause.
   */
  public UdmiException(String category, String message, Throwable cause) {
    super(message, cause);
    this.category = category;
  }

  /**
   * Gets the associated logging category.
   *
   * @return The category string.
   */
  public String getCategory() {
    return category;
  }
}
