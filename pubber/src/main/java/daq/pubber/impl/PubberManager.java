package daq.pubber.impl;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.fromJsonFileStrict;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.udmi.util.SiteModel;
import daq.pubber.impl.host.PubberPublisherHost;
import java.io.File;
import udmi.lib.base.ManagerBase;
import udmi.lib.intf.ManagerHost;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;

/**
 * Common manager class for all Pubber managers. Mainly just holds on to configuration and options.
 */
public class PubberManager extends ManagerBase {

  protected static final String LOG_PATH = "pubber/out";
  protected static final String PERSISTENT_STORE_FILE = "persistent_data.json";
  protected static final String PERSISTENT_TMP_FORMAT = "/tmp/pubber_%s_" + PERSISTENT_STORE_FILE;

  protected final PubberConfiguration config;
  protected final PubberOptions options;
  protected File outDir;

  /**
   * New instance.
   */
  public PubberManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration.deviceId);
    config = configuration;
    options = configuration.options;
  }

  @Override
  protected int getIntervalSec(Integer sampleRateSec) {
    return ofNullable(options.fixedSampleRate).orElse(super.getIntervalSec(sampleRateSec));
  }

  public PubberOptions getOptions() {
    return options;
  }

  public PubberConfiguration getPubberConfig() {
    return config;
  }

  protected static PubberConfiguration loadConfiguration(String configPath) {
    File configFile = new File(configPath);
    try {
      PubberConfiguration fromFile = fromJsonFileStrict(configFile, PubberConfiguration.class);
      ifNullThen(fromFile.options, () -> fromFile.options = new PubberOptions());
      return fromFile;
    } catch (Exception e) {
      throw new RuntimeException(
          format("While configuring from %s", configFile.getAbsolutePath()), e);
    }
  }

  protected static PubberConfiguration makeExplicitConfiguration(
      String iotProject, String sitePath, String deviceId, String serialNo) {
    PubberConfiguration configuration = new PubberConfiguration();
    configuration.iotProject = iotProject;
    configuration.sitePath = sitePath;
    configuration.deviceId = deviceId;
    configuration.serialNo = serialNo;
    configuration.options = new PubberOptions();
    return configuration;
  }

  protected static PubberConfiguration makeProxyConfiguration(
      ManagerHost host, String id, PubberConfiguration config) {
    PubberConfiguration proxyConfiguration = deepCopy(config);
    proxyConfiguration.deviceId = id;
    Metadata metadata = ((PubberPublisherHost) host).getSiteModel().getMetadata(id);
    proxyConfiguration.serialNo = catchToNull(() -> metadata.system.serial_no);
    return proxyConfiguration;
  }

  protected static File createOutDir(String serialNo) {
    File dir = new File(serialNo == null ? LOG_PATH : format("%s/%s", LOG_PATH, serialNo));
    ifTrueThen(!dir.exists(), () ->
        checkState(dir.mkdirs(), format("Could not make out dir %s", dir.getAbsolutePath())));
    return dir;
  }

  protected static File getPersistentStore(SiteModel siteModel, String deviceId) {
    return siteModel == null
        ? new File(format(PERSISTENT_TMP_FORMAT, deviceId))
        : new File(siteModel.getDeviceWorkingDir(deviceId), PERSISTENT_STORE_FILE);
  }

  // <editor-fold desc="Pubber options">
  public String getRedirectRegistry() {
    return options.redirectRegistry;
  }

  public boolean isBadVersion() {
    return isTrue(options.badVersion);
  }

  public boolean isNoFolder() {
    return isTrue(options.noFolder);
  }

  public boolean isNoState() {
    return isTrue(options.noState);
  }

  public boolean isBadState() {
    return isTrue(options.badState);
  }

  public boolean isTweakState() {
    return isTrue(options.tweakState);
  }

  public boolean isEmptyMissing() {
    return isTrue(options.emptyMissing);
  }

  public boolean isBarfConfig() {
    return isTrue(options.barfConfig);
  }

  public boolean isSmokeCheck() {
    return isTrue(options.smokeCheck);
  }

  public boolean isConfigStateDelay() {
    return isTrue(options.configStateDelay);
  }

  public boolean isBadCategory() {
    return isTrue(options.badCategory);
  }

  public boolean isMsTimestamp() {
    return isTrue(options.msTimestamp);
  }

  public boolean isNoStatus() {
    return isTrue(options.noStatus);
  }

  public boolean isDupeState() {
    return isTrue(options.dupeState);
  }

  public boolean isMessageTrace() {
    return isTrue(options.messageTrace);
  }

  public boolean isSkewClock() {
    return isTrue(options.skewClock);
  }

  public boolean isSpamState() {
    return isTrue(options.spamState);
  }

  public boolean isNoLog() {
    return isTrue(options.noLog);
  }

  public boolean isBadLevel() {
    return isTrue(options.badLevel);
  }

  public boolean isNoLastConfig() {
    return isTrue(options.noLastConfig);
  }

  public boolean isNoLastStart() {
    return isTrue(options.noLastStart);
  }

  public boolean isNoHardware() {
    return isTrue(options.noHardware);
  }

  public String getExtraField() {
    return options.extraField;
  }

  public Integer getFixedLogLevel() {
    return options.fixedLogLevel;
  }

  public String getSoftwareFirmwareValue() {
    return options.softwareFirmwareValue;
  }

  public boolean isNoWriteback() {
    return isTrue(options.noWriteback);
  }

  public boolean isNoPointState() {
    return isTrue(options.noPointState);
  }

  public String getExtraPoint() {
    return options.extraPoint;
  }

  public String getMissingPoint() {
    return options.missingPoint;
  }

  public boolean isNoProxy() {
    return isTrue(options.noProxy);
  }

  public boolean isExtraDevice() {
    return isTrue(options.extraDevice);
  }
  // </editor-fold>

  protected boolean isFastWrite() {
    return isTrue(options.fastWrite);
  }

  protected boolean isDelayWrite() {
    return isTrue(options.delayWrite);
  }
}
