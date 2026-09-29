// Single-file Java program (run with `java -cp <validator jar> SequenceCatalog.java`).
// It lives with the Workbench rather than in validator/ so the catalog can be read
// by reflection from the compiled validator without modifying validator sources.
// It uses only public validator APIs, since it is loaded by a separate class loader.

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.WithCapability;
import static com.google.common.base.Preconditions.checkState;
import static udmi.schema.Bucket.UNKNOWN_DEFAULT;

import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.Envelope.SubFolder;

/**
 * Enumerates every sequencer test compiled into the validator, without executing anything.
 *
 * <p>This is the authoritative test catalog: it reads the {@link Test}, {@link Feature},
 * {@link Summary} and {@link WithCapability} annotations directly from the compiled sequence
 * classes, so newly added and ALPHA-stage tests are always included. Classes are loaded without
 * static initialization, so no site model, config, or broker is required.
 */
public class SequenceCatalog {

  public static final int CATALOG_VERSION = 1;

  /**
   * Emit the full sequence catalog as JSON on stdout.
   *
   * @param args no arguments are accepted
   */
  public static void main(String[] args) {
    checkState(args.length == 0, "unrecognized command line arguments: " + String.join(" ", args));
    System.out.println(JsonUtil.stringify(buildCatalog()));
  }

  /**
   * Build the catalog from all sequence classes in the canonical sequences package.
   *
   * @return catalog of all sequencer tests
   */
  public static Catalog buildCatalog() {
    Set<String> classNames = Common.allClassesInPackage(ConfigSequences.class);
    checkState(!classNames.isEmpty(), "No sequence classes found in package "
        + ConfigSequences.class.getPackageName());
    List<Class<?>> classes = classNames.stream().map(Common::classForName)
        .collect(Collectors.toList());
    return buildCatalog(classes);
  }

  /**
   * Build the catalog from an explicit list of sequence classes.
   *
   * @param classes sequence classes to enumerate
   * @return catalog of all test methods declared by the given classes
   */
  public static Catalog buildCatalog(List<Class<?>> classes) {
    Map<String, Entry> byName = new HashMap<>();
    for (Class<?> clazz : classes) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getAnnotation(Test.class) == null) {
          continue;
        }
        Entry entry = describe(method);
        Entry previous = byName.put(entry.name, entry);
        checkState(previous == null, String.format("Duplicate sequence test name %s in %s and %s",
            entry.name, previous == null ? null : previous.declaring_class, entry.declaring_class));
      }
    }
    Catalog catalog = new Catalog();
    catalog.catalog_version = CATALOG_VERSION;
    catalog.sequences = byName.values().stream()
        .sorted(Comparator.comparing((Entry e) -> e.bucket).thenComparing(e -> e.name))
        .collect(Collectors.toList());
    return catalog;
  }

  private static Entry describe(Method method) {
    Feature feature = method.getAnnotation(Feature.class);
    final Summary summary = method.getAnnotation(Summary.class);
    Entry entry = new Entry();
    entry.name = method.getName();
    entry.declaring_class = method.getDeclaringClass().getName();
    entry.bucket = resolveBucket(feature, entry).value();
    entry.stage = (feature == null ? Feature.DEFAULT_STAGE : feature.stage()).name();
    entry.score = feature == null ? Feature.DEFAULT_SCORE : feature.score();
    entry.nostate = feature != null && feature.nostate();
    SubFolder facets = feature == null ? SubFolder.INVALID : feature.facets();
    entry.facets = facets == SubFolder.INVALID ? null : facets.value();
    entry.summary = summary == null ? null : summary.value();
    entry.ignored = method.getAnnotation(Ignore.class) != null;
    entry.timeout_ms = method.getAnnotation(Test.class).timeout();
    entry.capabilities = new ArrayList<>();
    Arrays.stream(method.getAnnotationsByType(WithCapability.class)).forEach(cap -> {
      CapabilityEntry capEntry = new CapabilityEntry();
      capEntry.name = cap.value().getSimpleName();
      capEntry.stage = cap.stage().name();
      entry.capabilities.add(capEntry);
    });
    return entry;
  }

  /**
   * Mirrors SequenceBase bucket resolution: implicit and explicit buckets are mutually exclusive.
   */
  private static Bucket resolveBucket(Feature feature, Entry entry) {
    if (feature == null) {
      return UNKNOWN_DEFAULT;
    }
    Bucket implicit = feature.value();
    Bucket explicit = feature.bucket();
    checkState(implicit == UNKNOWN_DEFAULT || explicit == UNKNOWN_DEFAULT,
        String.format("Both implicit and explicit buckets defined for %s#%s",
            entry.declaring_class, entry.name));
    return implicit == UNKNOWN_DEFAULT ? explicit : implicit;
  }

  /**
   * Complete catalog of sequencer tests.
   */
  @SuppressWarnings("MemberName")
  public static class Catalog {

    public int catalog_version;
    public List<Entry> sequences;
  }

  /**
   * Description of a single sequencer test method.
   */
  @SuppressWarnings("MemberName")
  public static class Entry {

    public String name;
    public String bucket;
    public String stage;
    public int score;
    public boolean nostate;
    public String facets;
    public String summary;
    public boolean ignored;
    public long timeout_ms;
    public String declaring_class;
    public List<CapabilityEntry> capabilities;
  }

  /**
   * Capability attached to a sequencer test.
   */
  @SuppressWarnings("MemberName")
  public static class CapabilityEntry {

    public String name;
    public String stage;
  }
}
