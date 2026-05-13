package daq.pubber.impl.blob;

import static com.google.udmi.util.JsonUtil.asMap;
import static udmi.schema.Category.BLOBSET_BLOB_APPLY_RESTART;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.lib.base.UdmiException.BlobIncompatibleException;
import udmi.lib.base.UdmiException.BlobParseException;
import udmi.lib.base.UdmiException.PayloadTooBigException;
import udmi.lib.blob.intf.BlobLifecycleHandler;
import udmi.lib.client.host.PublisherHost;
import udmi.schema.Operation.SystemMode;

/**
 * Implementation of the {@link BlobLifecycleHandler} for the Pubber emulator.
 * Manages the lifecycle of blobs, such as software modules,
 * through a two-phase deployment process: staging and activation.
 */
public class PubberBlobLifecycleHandler implements BlobLifecycleHandler {

  /**
   * The key used to identify the primary software module blob in the system state.
   */
  public static final String SOFTWARE_MODULE_KEY = "pubber_module";

  private final PublisherHost host;
  private final MockGitModuleEmulator moduleEmulator;
  private final Map<String, Handlers> blobHandlers = new HashMap<>();

  /**
   * Constructs a new handler and initializes the default module emulator.
   *
   * @param host   The publisher host providing access to device state and logging.
   * @param suffix A unique suffix for directory naming to avoid collisions.
   */
  public PubberBlobLifecycleHandler(PublisherHost host, String suffix) {
    this.host = host;

    String dynamicDir = "out/pubber_module_repo_" + suffix;
    this.moduleEmulator = new MockGitModuleEmulator(dynamicDir, host::info, host::notice,
        host::error);
    moduleEmulator.initialize();

    // Register handlers for supported blob types
    blobHandlers.put(SOFTWARE_MODULE_KEY, new Handlers(
        this::stagePubberModuleUpdate,
        this::activatePubberModuleUpdate
    ));

    updateModuleVersionInState();
  }

  /**
   * Checks if the specified blob name has a registered handler.
   *
   * @param blobName The name of the blob to check.
   * @return true if the blob is supported, false otherwise.
   */
  @Override
  public boolean isBlobSupported(String blobName) {
    return blobHandlers.containsKey(blobName);
  }

  /**
   * Fetches blob data from a URL with simulated error handling for testing.
   *
   * @param url The URL of the blob data.
   * @return The fetched byte array.
   * @throws PayloadTooBigException if the URL contains the "mock_oversize" trigger.
   */
  @Override
  public byte[] fetchBlobData(String url) {
    if (url != null && url.contains("mock_oversize")) {
      throw new PayloadTooBigException("Simulated payload too big");
    }
    return BlobLifecycleHandler.super.fetchBlobData(url);
  }

  /**
   * Routes the staging request to the appropriate registered handler.
   *
   * @param blobName The name of the blob.
   * @param payload  The payload data to stage.
   */
  @Override
  public void stageBlob(String blobName, String payload) {
    Handlers handlers = blobHandlers.get(blobName);
    if (handlers != null) {
      handlers.stager.accept(blobName, payload);
    }
  }

  /**
   * Routes the activation request to the appropriate registered handler.
   *
   * @param blobName The name of the blob to activate.
   */
  @Override
  public void activateBlob(String blobName) {
    Handlers handlers = blobHandlers.get(blobName);
    if (handlers != null) {
      handlers.activator.accept(blobName);
    }
  }

  /**
   * Synchronizes the module version from the emulator into the device state.
   */
  private void updateModuleVersionInState() {
    if (moduleEmulator != null) {
      if (host.getDeviceState().system.software == null) {
        host.getDeviceState().system.software = new HashMap<>();
      }
      host.getDeviceState().system.software.put(SOFTWARE_MODULE_KEY,
          moduleEmulator.getModuleVersion());
      host.markStateDirty();
    }
  }

  // --- Specific Handler Logic ---

  /**
   * Validates and prepares a software module update.
   */
  private void stagePubberModuleUpdate(String blobName, String payload) {
    Map<String, Object> payloadMap;
    try {
      payloadMap = asMap(payload);
    } catch (Exception e) {
      throw new BlobParseException("Failed to parse blob payload for " + blobName);
    }
    if ("incompatible".equals(payloadMap.get("trigger"))) {
      throw new BlobIncompatibleException("Simulated incompatibility for " + blobName);
    }
    moduleEmulator.updateTo(payloadMap);
    updateModuleVersionInState();
  }

  /**
   * Finalizes the software module update by triggering a system restart.
   */
  private void activatePubberModuleUpdate(String blobName) {
    host.logEvent(BLOBSET_BLOB_APPLY_RESTART, "Restart required for " + blobName);
    host.notice("Post-processing Git OTA update. Restarting...");
    host.getDeviceManager().systemLifecycle(SystemMode.RESTART);
  }

  /**
   * Internal record to group staging and activation logic for a specific blob type.
   *
   * @param stager    Logic to execute during Phase 1 (Staging).
   * @param activator Logic to execute during Phase 2 (Activation).
   */
  private record Handlers(BiConsumer<String, String> stager, Consumer<String> activator) {

    /**
     * Compact constructor to ensure activator is never null.
     */
    private Handlers(BiConsumer<String, String> stager, Consumer<String> activator) {
      this.stager = stager;
      this.activator = activator != null ? activator : (name) -> { };
    }
  }
}
