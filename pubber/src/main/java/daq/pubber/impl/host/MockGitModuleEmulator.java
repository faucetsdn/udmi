package daq.pubber.impl.host;

import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.String.format;

import java.io.File;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import udmi.lib.base.UdmiException.BlobDependencyMismatchException;
import udmi.lib.base.UdmiException.BlobIncompatibleException;
import udmi.schema.PubberOptions;

/**
 * Mock emulator for Git modules used in OTA updates.
 */
public class MockGitModuleEmulator {


  private final File repoDir;
  private final Consumer<String> infoLogger;
  private final Consumer<String> noticeLogger;
  private final Consumer<String> errorLogger;
  private final PubberOptions options;
  private boolean inMemoryFallback = false;

  /**
   * Creates a new instance of MockGitModuleEmulator.
   *
   * @param softwareModuleDir The directory for the software module.
   * @param options           The pubber options.
   * @param infoLogger        Logger for info messages.
   * @param noticeLogger      Logger for notice messages.
   * @param errorLogger       Logger for error messages.
   */
  public MockGitModuleEmulator(String softwareModuleDir, PubberOptions options,
      Consumer<String> infoLogger, Consumer<String> noticeLogger, Consumer<String> errorLogger) {
    this.repoDir = new File(softwareModuleDir);
    this.options = options;
    this.infoLogger = infoLogger;
    this.noticeLogger = noticeLogger;
    this.errorLogger = errorLogger;
  }

  /**
   * Initializes the module for OTA updates.
   */
  public void initModuleForOtaUpdates() {
    try {
      if (repoDir.exists()) {
        FileUtils.deleteDirectory(repoDir);
      }
      if (!repoDir.mkdirs()) {
        throw new RuntimeException("Failed to create source directory");
      }

      infoLogger.accept(format("Initializing mock module in %s", repoDir.getAbsolutePath()));
      try {
        runCommandInDir(repoDir, "git", "init");
        runCommandInDir(repoDir, "git", "config", "user.name", "Pubber");
        runCommandInDir(repoDir, "git", "config", "user.email", "pubber@udmi.io");

        File versionFile = new File(repoDir, "version.txt");
        FileUtils.writeStringToFile(versionFile, "v1", "UTF-8");
        runCommandInDir(repoDir, "git", "add", ".");
        runCommandInDir(repoDir, "git", "commit", "-m", "v1");
        runCommandInDir(repoDir, "git", "tag", "v1");

        FileUtils.writeStringToFile(versionFile, "v2", "UTF-8");
        runCommandInDir(repoDir, "git", "add", ".");
        runCommandInDir(repoDir, "git", "commit", "-m", "v2");
        runCommandInDir(repoDir, "git", "tag", "v2");
        infoLogger.accept("Isolated repo initialized successfully.");
      } catch (Exception e) {
        infoLogger.accept(
            "Git execution failed on host. Falling back to abstract in-memory logic.");
        inMemoryFallback = true;
      }
    } catch (Exception e) {
      errorLogger.accept("While initializing isolated repo: " + e.getMessage());
    }
  }

  /**
   * Handles an OTA update with the given payload.
   *
   * @param payload The update payload (e.g., commit hash).
   */
  public void handleOtaUpdate(String payload) {
    if (isTrue(options.hardwareIncompatible)) {
      safeSleep(2000);
      throw new BlobIncompatibleException("Hardware incompatible static failure intentions");
    }
    if (isTrue(options.softwareDependencyMismatch)) {
      safeSleep(2000);
      throw new BlobDependencyMismatchException(
          "Software dependencies temporal temporal prerequisite intentions");
    }
    String commitHash = payload.trim();
    infoLogger.accept(format("Triggering mock OTA update to commit %s", commitHash));

    if (inMemoryFallback) {
      infoLogger.accept("Simulating OTA update delay in-memory...");
      safeSleep(2000);
      noticeLogger.accept("Mock Git OTA update completed abstractly.");
      return;
    }

    if (!repoDir.exists()) {
      throw new RuntimeException("Isolated repo directory not found");
    }

    try {
      infoLogger.accept("Simulating OTA update delay...");
      safeSleep(2000);
      runCommandInDir(repoDir, "git", "checkout", commitHash);
      noticeLogger.accept("Git OTA update completed successfully.");
    } catch (Exception e) {
      throw new RuntimeException("Git operation failed", e);
    }
  }

  private void runCommandInDir(File dir, String... command) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(dir);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    int exitCode = p.waitFor();
    if (exitCode != 0) {
      throw new RuntimeException(format("Command failed with exit code %d", exitCode));
    }
  }
}
