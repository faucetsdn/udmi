package daq.pubber.impl.provider;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static udmi.lib.ProtocolFamily.BACNET;
import static udmi.lib.client.manager.DiscoveryManager.shouldEnumerate;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.SiteModel;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.lib.base.ManagerBase;
import udmi.lib.client.manager.LocalnetManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscovery;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;
import udmi.schema.StateSystemHardware;
import udmi.schema.SystemDiscoveryData;

/**
 * Base class for functionality supporting all pubber discovery providers.
 */
public class PubberProviderBase extends ManagerBase {

  public static final int BACNET_IPV4_PORT = 47808;
  private final String family;
  private final LocalnetManager localnetHost;
  private String selfAddr;
  private boolean enumerate;
  private BiConsumer<String, DiscoveryEvents> publisher;
  private Map<String, Metadata> allDevices;
  private SiteModel siteModel;
  private FamilyDiscoveryConfig config;

  /**
   * Create a new instance of a generic family provider.
   */
  public PubberProviderBase(ManagerHost host, String family, String deviceId) {
    super(host, deviceId);
    this.family = family;
    localnetHost = ((LocalnetManager) host);
  }

  protected void startScan(FamilyDiscoveryConfig config,
      BiConsumer<String, DiscoveryEvents> publisher) {
    this.config = config;
    this.publisher = publisher;
    this.enumerate = config.addrs != null || shouldEnumerate(config.depth, Depth.DETAILS);

    if (config.depth == Depth.BUCKETS) {
      allDevices = new LinkedHashMap<>();
      Set<String> seenNetworks = new HashSet<>();
      siteModel.allMetadata().entrySet().stream()
          .filter(this::isValidTargetDevice)
          .forEach(entry -> {
            String network = getFamilyNetwork(entry.getKey());
            if (nonNull(network) && seenNetworks.add(network)) {
              allDevices.put(entry.getKey(), entry.getValue());
            }
          });
    } else {
      allDevices = siteModel.allMetadata().entrySet().stream()
          .filter(this::isValidTargetDevice)
          .collect(toMap(Entry::getKey, Entry::getValue));
    }
  }

  private DiscoveryEvents augmentSend(String deviceId, boolean enumerate) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.network = getFamilyNetwork(deviceId);
    event.families = getDiscoveredFamilies(deviceId);
    if (shouldEnumerate(config.depth, Depth.ENTRIES)) {
      event.addr = getFamilyAddr(deviceId);
    }
    if (shouldEnumerate(config.depth, Depth.SYSTEM)) {
      event.system = getDiscoveredSystem(deviceId);
    }
    try {
      event.refs = ifTrueGet(enumerate, () -> getDiscoveredRefs(getAllDevices().get(deviceId)));
    } catch (Exception e) {
      event.status = new udmi.schema.Entry();
      event.status.level = Level.ERROR.value();
    }
    return event;
  }

  private Map<String, FamilyDiscovery> getDiscoveredFamilies(String deviceId) {
    if (!BACNET.equals(family)) {
      return null;
    }
    if (config.depth == Depth.BUCKETS) {
      FamilyDiscovery ipv4 = new FamilyDiscovery();
      ipv4.port = BACNET_IPV4_PORT;
      return Map.of("ipv4", ipv4);
    }
    Map<String, FamilyLocalnetModel> localnetFamilies =
        catchToNull(() -> siteModel.getMetadata(deviceId).localnet.families);
    Map<String, FamilyDiscovery> discovered = new HashMap<>();
    if (localnetFamilies != null) {
      localnetFamilies.forEach((famKey, famModel) -> {
        if (("ipv4".equals(famKey) || "ether".equals(famKey))
            && nonNull(famModel) && nonNull(famModel.addr)) {
          FamilyDiscovery fd = new FamilyDiscovery();
          fd.addr = famModel.addr;
          discovered.put(famKey, fd);
        }
      });
    }
    FamilyDiscovery ipv4 = discovered.computeIfAbsent("ipv4", k -> new FamilyDiscovery());
    ipv4.port = BACNET_IPV4_PORT;
    return discovered;
  }

  private SystemDiscoveryData getDiscoveredSystem(String deviceId) {
    Metadata metadata = getAllDevices().get(deviceId);
    SystemDiscoveryData system = new SystemDiscoveryData();
    system.name = catchToNull(() -> metadata.system.physical_tag.asset.name);
    system.serial_no = catchToNull(() -> metadata.system.serial_no);
    if (catchToNull(() -> metadata.system.hardware) != null) {
      system.hardware = new StateSystemHardware();
      system.hardware.make = metadata.system.hardware.make;
      system.hardware.model = metadata.system.hardware.model;
    }
    return system;
  }

  protected Map<String, Metadata> getAllDevices() {
    return allDevices;
  }

  private Map<String, RefDiscovery> getDiscoveredRefs(Metadata entry) {
    Map<String, PointPointsetModel> points = catchToNull(() -> entry.pointset.points);
    return ofNullable(points).orElse(ImmutableMap.of()).entrySet().stream()
        .filter(point -> nonNull(point.getValue().ref))
        .map(this::pointsetToRef)
        .collect(toMap(Entry::getKey, Entry::getValue));
  }

  private Entry<String, RefDiscovery> pointsetToRef(Entry<String, PointPointsetModel> e) {
    return new SimpleEntry<>(e.getValue().ref, getModelPointRef(e));
  }

  /**
   * Get a ref value that describes a point for self enumeration.
   */
  private RefDiscovery getModelPointRef(Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = entry.getKey();
    return refDiscovery;
  }

  /**
   * Set the site model.
   */
  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
    selfAddr = getFamilyAddr(deviceId);
    addStateMapEntry();
  }

  private boolean isValidTargetDevice(Entry<String, Metadata> entry) {
    String deviceId = entry.getKey();
    return nonNull(getFamilyAddr(deviceId))
        && (config.networks == null || config.networks.contains(getFamilyNetwork(deviceId)));
  }

  private void addStateMapEntry() {
    FamilyLocalnetState stateEntry = new FamilyLocalnetState();
    stateEntry.addr = selfAddr;
    localnetHost.update(family, stateEntry);
  }

  protected Consumer<String> getResultPublisher() {
    return deviceId -> {
      debug(format("Sending %s result for %s "
          + "%s", family, deviceId, getFamilyAddr(deviceId)));
      publisher.accept(deviceId, augmentSend(deviceId, enumerate));
    };
  }

  private String getFamilyNetwork(String deviceId) {
    return catchToNull(() -> siteModel.getMetadata(deviceId).localnet.families.get(family).network);
  }

  private String getFamilyAddr(String deviceId) {
    return catchToNull(() -> siteModel.getMetadata(deviceId).localnet.families.get(family).addr);
  }

}
