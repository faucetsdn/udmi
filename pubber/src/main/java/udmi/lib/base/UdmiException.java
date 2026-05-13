package udmi.lib.base;

/**
 * Container for UDMI-specific exceptions.
 */
public class UdmiException {

  /**
   * Exception thrown when a blob cannot be parsed (e.g., invalid JSON format).
   */
  public static class BlobParseException extends RuntimeException {

    public BlobParseException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when there is a hash mismatch during integrity verification.
   */
  public static class HashMismatchException extends RuntimeException {

    public HashMismatchException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when a blob is incompatible with the current hardware or software version.
   */
  public static class BlobIncompatibleException extends RuntimeException {

    public BlobIncompatibleException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when the fetched payload exceeds the device's processing limits.
   */
  public static class PayloadTooBigException extends RuntimeException {

    public PayloadTooBigException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when an error occurs during the application/installation of a blob.
   */
  public static class BlobApplyFailureException extends RuntimeException {

    public BlobApplyFailureException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when a blob update process is explicitly aborted by the system.
   */
  public static class BlobAbortException extends RuntimeException {

    public BlobAbortException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when a system fails to apply a blob and triggers a rollback to the previous
   * state.
   */
  public static class BlobRollbackException extends RuntimeException {

    public BlobRollbackException(String message) {
      super(message);
    }
  }
}
