package udmi.lib.blob.intf;

import static com.google.udmi.util.GeneralUtils.sha256;
import static udmi.lib.blob.BlobFetcherRegistry.getFetcher;

import udmi.lib.base.UdmiException.HashMismatchException;

/**
 * Defines the lifecycle and handling of BLOB updates on a device.
 *
 * <p>Implementations of this interface manage a strict two-phase deployment process
 * (staging followed by activation). This ensures that the device can successfully
 * publish its updated state to the cloud before executing any disruptive actions,
 * such as a system restart.
 */
public interface BlobLifecycleHandler {

  /**
   * Fetches the binary data for a blob from the provided endpoint.
   *
   * @param url The URL from which to download the blob.
   * @return The downloaded blob data as a byte array.
   */
  default byte[] fetchBlobData(String url) {
    return getFetcher(url).fetch(url);
  }

  /**
   * Verifies the integrity of the downloaded blob against an expected SHA-256 hash.
   *
   * @param dataBytes      The downloaded blob data.
   * @param expectedSha256 The expected SHA-256 hash string.
   * @throws HashMismatchException if the calculated hash does not match the expected hash.
   */
  default void verifyBlobIntegrity(byte[] dataBytes, String expectedSha256) {
    String dataSha256 = sha256(dataBytes);
    if (!dataSha256.equals(expectedSha256)) {
      throw new HashMismatchException("Blob data hash mismatch");
    }
  }

  // -- Abstract methods for the specific device implementation --

  /**
   * Determines whether the device application supports handling the specified blob.
   *
   * @param blobName The name or identifier of the blob.
   * @return true if the blob is supported, false otherwise.
   */
  boolean isBlobSupported(String blobName);

  /**
   * Stages the blob payload on the device without triggering disruptive actions.
   *
   * <p>This is Phase 1 of the deployment. Implementations should handle file saving,
   * parsing, or preparation here. Crucially, this method must NOT restart the device
   * or interrupt core operations. It must exit cleanly so the system can safely
   * publish the updated state before proceeding to activation.
   *
   * @param blobName The name or identifier of the blob.
   * @param payload  The blob payload data to be staged.
   */
  void stageBlob(String blobName, String payload);

  /**
   * Activates the staged blob, executing any necessary disruptive actions.
   *
   * <p>This is Phase 2 of the deployment and is invoked ONLY after the updated
   * device state has been successfully published. Implementations can safely
   * perform actions like device restarts, service reloads, or firmware applications
   * here without risking state de-synchronization.
   *
   * @param blobName The name or identifier of the blob to activate.
   */
  default void activateBlob(String blobName) {
  }

}