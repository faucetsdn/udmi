package daq.pubber.impl.blob;

import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import udmi.lib.base.UdmiException.BlobAbortException;
import udmi.lib.base.UdmiException.BlobApplyFailureException;
import udmi.lib.base.UdmiException.BlobIncompatibleException;
import udmi.lib.base.UdmiException.BlobRollbackException;

/**
 * Mock emulator for Git modules used in OTA updates.
 */
public class MockGitModuleEmulator {

  private static final String VERSION_KEY = "version";
  private static final String SIMULATE_KEY = "simulate";
  private static final String UNKNOWN_VERSION = "unknown";
  private static final int SIMULATED_DELAY_MS = 2000;

  /**
   * The directory where the mock JGit repository is initialized.
   */
  private final File repoDir;

  private final Consumer<String> infoLogger;
  private final Consumer<String> noticeLogger;
  private final Consumer<String> errorLogger;
  private boolean inMemoryFallback = false;

  /**
   * Creates a new instance of MockGitModuleEmulator.
   *
   * @param softwareModuleDir The directory for the software module.
   * @param infoLogger        Logger for info messages.
   * @param noticeLogger      Logger for notice messages.
   * @param errorLogger       Logger for error messages.
   */
  public MockGitModuleEmulator(String softwareModuleDir, Consumer<String> infoLogger,
      Consumer<String> noticeLogger, Consumer<String> errorLogger) {
    this.repoDir = new File(softwareModuleDir);
    this.infoLogger = infoLogger;
    this.noticeLogger = noticeLogger;
    this.errorLogger = errorLogger;
  }

  /**
   * Initializes the module by creating a local JGit repository with mock history. If file system or
   * JGit operations fail, it sets an in-memory fallback flag.
   */
  public void initialize() {
    try {
      prepareDirectory();
      setupMockRepository();
    } catch (Exception e) {
      errorLogger.accept("Critical failure during initialization: " + e.getMessage());
      inMemoryFallback = true;
    }
  }

  private void prepareDirectory() throws IOException {
    if (repoDir.exists()) {
      FileUtils.deleteDirectory(repoDir);
    }
    if (!repoDir.mkdirs()) {
      throw new IOException("Failed to create directory: " + repoDir.getAbsolutePath());
    }
  }

  private void setupMockRepository() {
    infoLogger.accept("Initializing mock JGit module in " + repoDir.getAbsolutePath());
    try (Git git = Git.init().setDirectory(repoDir).call()) {
      createCommit(git, "v1");
      createCommit(git, "v2");
      git.checkout().setName("v1").call();
      infoLogger.accept("Isolated JGit repo initialized successfully.");
    } catch (Exception e) {
      infoLogger.accept("JGit init failed, using in-memory fallback: " + e.getMessage());
      inMemoryFallback = true;
    }
  }

  private void createCommit(Git git, String version) throws Exception {
    File versionFile = new File(repoDir, "version.txt");
    FileUtils.writeStringToFile(versionFile, version, UTF_8);
    git.add().addFilepattern(".").call();
    git.commit()
        .setMessage(version)
        .setAuthor("Pubber", "pubber@udmi.io")
        .call();
    git.tag().setName(version).call();
  }

  /**
   * Handles an OTA update by parsing the payload and checking out the specified version. This
   * method also processes "simulate" keys in the payload to trigger mock errors.
   *
   * @param payloadMap The JSON payload containing update instructions (version and behavior).
   * @throws RuntimeException if the version is missing or JGit checkout fails.
   */
  public void updateTo(Map<String, Object> payloadMap) {
    String version = Optional.ofNullable((String) payloadMap.get(VERSION_KEY))
        .map(String::trim)
        .orElseThrow(() -> new BlobIncompatibleException("Missing version in payload"));

    handleSimulatedBehaviors((String) payloadMap.get(SIMULATE_KEY));

    infoLogger.accept(format("Updating pubber module to: %s", version));
    safeSleep(SIMULATED_DELAY_MS);

    if (inMemoryFallback) {
      noticeLogger.accept("Mock module update completed (In-Memory).");
      return;
    }

    try (Git git = Git.open(repoDir)) {
      git.checkout().setName(version).call();
      noticeLogger.accept("Mock module update completed successfully.");
    } catch (Exception e) {
      throw new BlobApplyFailureException(
          "JGit checkout failed for version: " + version + e.getMessage());
    }
  }

  private void handleSimulatedBehaviors(String behavior) {
    if (behavior == null) {
      return;
    }

    safeSleep(SIMULATED_DELAY_MS);
    switch (behavior.toLowerCase()) {
      case "incompatible" -> throw new BlobIncompatibleException("Hardware incompatible");
      case "apply_failure" -> throw new BlobApplyFailureException("Simulated apply failure");
      case "abort" -> throw new BlobAbortException("Simulated abort");
      case "rollback" -> throw new BlobRollbackException("Simulated rollback");
      default -> infoLogger.accept("No simulated error for behavior: " + behavior);
    }
  }

  /**
   * Retrieves the current version of the module.
   *
   * @return The current tag/commit hash, or "unknown" if the repository is not available.
   */
  public String getModuleVersion() {
    if (inMemoryFallback || !repoDir.exists()) {
      return UNKNOWN_VERSION;
    }

    try (Git git = Git.open(repoDir)) {
      ObjectId head = git.getRepository().resolve("HEAD");
      return head != null ? git.describe().setTarget(head).call() : UNKNOWN_VERSION;
    } catch (Exception e) {
      errorLogger.accept("Failed to resolve version: " + e.getMessage());
      return UNKNOWN_VERSION;
    }
  }
}
