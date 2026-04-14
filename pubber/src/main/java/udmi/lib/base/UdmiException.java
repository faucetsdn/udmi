package udmi.lib.base;

public class UdmiException {

  public static class HashMismatchException extends RuntimeException {
    public HashMismatchException(String message) {
      super(message);
    }
  }

  public static class BlobIncompatibleException extends RuntimeException {
    public BlobIncompatibleException(String message) {
      super(message);
    }
  }

  public static class BlobDependencyMismatchException extends RuntimeException {
    public BlobDependencyMismatchException(String message) {
      super(message);
    }
  }

}
