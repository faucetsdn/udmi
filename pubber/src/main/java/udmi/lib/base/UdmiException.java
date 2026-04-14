package udmi.lib.base;

/**
 * Container for UDMI-specific exceptions.
 */
public class UdmiException {

  /**
   * Exception thrown when there is a hash mismatch.
   */
  public static class HashMismatchException extends RuntimeException {
    public HashMismatchException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when a blob is incompatible.
   */
  public static class BlobIncompatibleException extends RuntimeException {
    public BlobIncompatibleException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when there is a blob dependency mismatch.
   */
  public static class BlobDependencyMismatchException extends RuntimeException {
    public BlobDependencyMismatchException(String message) {
      super(message);
    }
  }

}
