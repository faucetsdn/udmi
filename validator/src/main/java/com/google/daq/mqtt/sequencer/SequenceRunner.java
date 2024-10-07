package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.sequencer.SequenceBase.getSequencerStateFile;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;
import static udmi.schema.FeatureDiscovery.FeatureStage.BETA;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.WebServerRunner;
import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.udmi.util.Common;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.MetadataException;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jetbrains.annotations.TestOnly;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FeatureDiscovery.FeatureStage;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.SequenceValidationState.SequenceResult;

/**
 * Custom test runner that can execute a specific method to test.
 */
public class SequenceRunner {

  private static final FeatureStage DEFAULT_MIN_STAGE = BETA;
  private static final String DEFAULT_CONFIG = "/tmp/sequencer_config.json";
  private static final String CONFIG_ENV = "SEQUENCER_CONFIG";
  private static final String CONFIG_PATH =
      Objects.requireNonNullElse(System.getenv(CONFIG_ENV), DEFAULT_CONFIG);
  private static final int EXIT_STATUS_SUCCESS = 0;
  private static final int EXIST_STATUS_FAILURE = 1;
  private static final String TOOL_ROOT = "..";
  private static final List<String> failures = new ArrayList<>();
  private static final Map<String, SequenceResult> allTestResults = new TreeMap<>();
  private static final List<String> SHARD_LIST = new ArrayList<>();
  private final Set<String> sequenceClasses = new TreeSet<>(
      Common.allClassesInPackage(ConfigSequences.class));
  private List<String> targets = List.of();

  /**
   * Thundercats are go.
   *
   * @param args Test classes/method to test.
   */
  public static void main(String[] args) {
    System.exit(processResult(Arrays.asList(args)));
  }

  /**
   * Execute sequence tests.
   *
   * @param targets individual tests to run
   * @return status code
   */
  public static int processResult(List<String> targets) {
    checkState(targets.isEmpty(), "unrecognized command line arguments");
    SequenceRunner sequenceRunner = new SequenceRunner();
    sequenceRunner.process();
    return sequenceRunner.resultCode();
  }

  private static SequenceRunner processConfig(ExecutionConfiguration config) {
    SequenceBase.exeConfig = config;
    SequenceRunner sequenceRunner = new SequenceRunner();
    SequenceBase.setDeviceId(config.device_id);
    sequenceRunner.process();
    return sequenceRunner;
  }

  /**
   * Handle a parameterized request to run a sequence on a device.
   *
   * @param params parameters for request
   */
  public static void handleRequest(Map<String, String> params) {
    final String sitePath = params.remove(WebServerRunner.SITE_PARAM);
    final String projectId = params.remove(WebServerRunner.PROJECT_PARAM);
    final String serialNo = params.remove(WebServerRunner.SERIAL_PARAM);
    final String deviceId = params.remove(WebServerRunner.DEVICE_PARAM);
    final String testMode = params.remove(WebServerRunner.TEST_PARAM);

    ExecutionConfiguration config = new ExecutionConfiguration();
    config.project_id = projectId;
    config.site_model = sitePath;
    config.device_id = deviceId;
    config.serial_no = Optional.ofNullable(serialNo).orElse(SequenceBase.SERIAL_NO_MISSING);
    config.log_level = Level.INFO.name();
    config.udmi_version = Common.getUdmiVersion();
    config.udmi_root = TOOL_ROOT;
    config.alt_project = testMode; // Sekrit hack for enabling mock components.

    SiteModel siteModel = new SiteModel(sitePath, config);
    siteModel.initialize();
    config.key_file = siteModel.validatorKey();

    failures.clear();
    allTestResults.clear();

    SequenceBase.resetState();

    if (deviceId != null) {
      SequenceRunner.processConfig(config);
    } else {
      siteModel.forEachDeviceId(siteDeviceId -> {
        config.device_id = siteDeviceId;
        SequenceRunner.processConfig(config);
      });
    }
  }

  public static List<String> getFailures() {
    return failures;
  }

  public static Map<String, SequenceResult> getAllTests() {
    return allTestResults;
  }

  /**
   * Check if a particular feature stage should be processed given the configured level.
   *
   * @param query stage to check
   */
  public static boolean processStage(FeatureStage query) {
    return processStage(query, getFeatureMinStage());
  }

  @TestOnly
  static boolean processStage(FeatureStage query, FeatureStage config) {
    boolean exact = ofNullable(SequenceBase.exeConfig.min_stage)
        .map(value -> value.startsWith("=")).orElse(false);
    return exact ? query == config : query.compareTo(config) >= 0;
  }

  private static FeatureStage getFeatureMinStage() {
    return ofNullable(SequenceBase.exeConfig.min_stage)
        .map(value -> value.startsWith("=") ? value.substring(1) : value)
        .map(FeatureStage::valueOf).orElse(DEFAULT_MIN_STAGE);
  }

  static ExecutionConfiguration ensureExecutionConfig() {
    if (SequenceBase.exeConfig != null) {
      return SequenceBase.exeConfig;
    }
    if (CONFIG_PATH == null || CONFIG_PATH.equals("")) {
      throw new RuntimeException(CONFIG_ENV + " env not defined.");
    }
    final File configFile = new File(CONFIG_PATH);
    try {
      System.err.println("Reading config file " + configFile.getAbsolutePath());
      ExecutionConfiguration exeConfig = ConfigUtil.readValidatorConfig(configFile);
      String udmiNamespace = exeConfig.udmi_namespace;
      exeConfig.udmi_namespace = null; // Prevent having this processed twice.
      SiteModel model = new SiteModel(exeConfig.site_model, exeConfig);
      model.initialize();
      exeConfig.udmi_namespace = udmiNamespace;
      reportLoadingErrors(model, exeConfig.device_id);
      exeConfig.cloud_region = ofNullable(exeConfig.cloud_region)
          .orElse(model.getCloudRegion());
      exeConfig.registry_id = ofNullable(exeConfig.registry_id)
          .orElse(model.getRegistryId());
      exeConfig.reflect_region = ofNullable(exeConfig.reflect_region)
          .orElse(model.getReflectRegion());
      return exeConfig;
    } catch (Exception e) {
      throw new RuntimeException("While loading " + configFile, e);
    }
  }

  private static void reportLoadingErrors(SiteModel model, String deviceId) {
    checkState(model.allDeviceIds().contains(deviceId),
        format("device_id %s not found in site model", deviceId));
    Metadata metadata = model.getMetadata(deviceId);
    if (metadata instanceof MetadataException metadataException) {
      System.err.println(
          "Device loading error: " + friendlyStackTrace(metadataException.exception));
    }
  }

  private int resultCode() {
    if (failures == null) {
      throw new RuntimeException("Sequences have not been processed");
    }
    int exitCode = failures.isEmpty() ? EXIT_STATUS_SUCCESS : EXIST_STATUS_FAILURE;
    System.err.printf("Found %d test failures, exit code %d%n", failures.size(), exitCode);
    return exitCode;
  }

  private void process() {
    try {
      processRaw();
      SequenceBase.processComplete(null);
    } catch (Exception e) {
      e.printStackTrace();
      SequenceBase.processComplete(e);
    }
  }

  private void processRaw() {
    if (sequenceClasses.isEmpty()) {
      throw new RuntimeException("No testing classes found");
    }
    System.err.println("Target sequence classes:\n  " + Joiner.on("\n  ").join(sequenceClasses));
    SequenceBase.exeConfig = ensureExecutionConfig();
    SequenceBase.initializeValidationState();
    targets = ofNullable(SequenceBase.exeConfig.sequences).orElseGet(ImmutableList::of);
    boolean enableAllBuckets = shouldExecuteAll() || !targets.isEmpty();
    SequenceBase.enableAllBuckets(enableAllBuckets);
    Set<String> remainingMethods = new HashSet<>(targets);
    int runCount = 0;
    for (String className : sequenceClasses) {
      Class<?> clazz = Common.classForName(className);
      List<Request> requests = new ArrayList<>();
      List<String> runMethods = getRunMethods(clazz);
      if (runMethods.isEmpty()) {
        System.err.println("Found target methods: none for class " + className);
        continue;
      }
      System.err.printf("Found target methods: %s%n", CSV_JOINER.join(runMethods));
      for (String method : runMethods) {
        System.err.println("Running target " + clazz.getName() + "#" + method);
        requests.add(Request.method(clazz, method));
        remainingMethods.remove(method);
      }
      for (Request request : requests) {
        Result result = new JUnitCore().run(request);
        List<Failure> theseFailures = result.getFailures();
        failures.addAll(summarizeFailures(theseFailures));
        runCount += result.getRunCount();
        List<String> failures = theseFailures.stream().map(Failure::getException)
            .filter(failure -> failure instanceof IllegalArgumentException)
            .map(Throwable::getMessage).toList();
        checkState(failures.isEmpty(), "Fatal system errors: " + CSV_JOINER.join(failures));
      }
    }

    if (!remainingMethods.isEmpty()) {
      throw new RuntimeException("Failed to find " + Joiner.on(", ").join(remainingMethods));
    }

    if (runCount <= 0) {
      throw new RuntimeException("No tests were executed!");
    }

    System.err.println();
    System.err.printf("Found %d test execution failures.%n", failures.size());
    failures.forEach(System.err::println);
    Map<SequenceResult, Long> resultCounts = allTestResults.entrySet().stream()
        .collect(Collectors.groupingBy(Entry::getValue, Collectors.counting()));
    resultCounts.forEach(
        (key, value) -> System.err.println("Sequencer result count " + key.name() + " = " + value));
    String stateAbsolutePath = getSequencerStateFile().getAbsolutePath();
    System.err.println("Sequencer state summary in " + stateAbsolutePath);
  }

  private Collection<String> summarizeFailures(List<Failure> failures) {
    return ifTrueGet(failures.isEmpty(), ImmutableList::of,
        ImmutableList.of(CSV_JOINER.join(failures.stream().map(this::getFailureMessage).toList())));
  }

  private String getFailureMessage(Failure failure) {
    ExecutionConfiguration exeConfig = SequenceBase.exeConfig;
    String failureKey = exeConfig.device_id + "/" + failure.getDescription().getMethodName();
    return failureKey + ": " + friendlyStackTrace(failure.getException());
  }

  private boolean shouldShardMethod(String method) {
    ExecutionConfiguration exeConfig = SequenceBase.exeConfig;
    if (exeConfig.shard_count == null) {
      return true;
    }

    int base = SHARD_LIST.indexOf(method);
    boolean alreadyPresent = base >= 0;
    int index = alreadyPresent ? base : (SHARD_LIST.add(method) ? SHARD_LIST.size() - 1 : -1);
    return targets.contains(method) || (index % exeConfig.shard_count) == exeConfig.shard_index;
  }

  private List<String> getRunMethods(Class<?> clazz) {
    List<String> methods = Arrays.stream(clazz.getMethods()).filter(this::isTestMethod)
        .filter(this::shouldProcessMethod).map(Method::getName).toList();

    // Pre-process the entire list for shard stability independent of any other filtering.
    methods.stream().sorted().forEach(this::shouldShardMethod);

    return methods.stream().filter(this::shouldShardMethod).filter(this::isTargetMethod).toList();
  }

  private boolean isTestMethod(Method method) {
    return method.getAnnotation(Test.class) != null;
  }

  private boolean isTargetMethod(String methodName) {
    return targets.isEmpty() || targets.contains(methodName);
  }

  private boolean shouldExecuteAll() {
    return (getFeatureMinStage().compareTo(ALPHA)) <= 0;
  }

  private boolean shouldProcessMethod(Method method) {
    Test test = method.getAnnotation(Test.class);
    if (test == null) {
      return false;
    }
    // If the target is explicitly indicated, then we should test it regardless of annotation.
    if (targets.contains(method.getName())) {
      return true;
    }
    Feature annotation = method.getAnnotation(Feature.class);
    return processStage(annotation == null ? Feature.DEFAULT_STAGE : annotation.stage());
  }
}
