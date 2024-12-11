
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery Events
 * <p>
 * [Discovery result](../docs/specs/discovery.md) with implicit discovery
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "generation",
    "status",
    "scan_family",
    "scan_addr",
    "event_no",
    "families",
    "registries",
    "devices",
    "points",
    "refs",
    "features",
    "cloud_model",
    "system"
})
public class DiscoveryEvents {

    /**
     * RFC 3339 UTC timestamp the discover telemetry event was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the discover telemetry event was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * The event's discovery scan trigger's generation timestamp
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("The event's discovery scan trigger's generation timestamp")
    public Date generation;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    @JsonProperty("scan_family")
    public String scan_family;
    /**
     * The primary address of the device (for scan_family)
     * 
     */
    @JsonProperty("scan_addr")
    @JsonPropertyDescription("The primary address of the device (for scan_family)")
    public java.lang.String scan_addr;
    /**
     * The active or passive series number of this result (matches reported state values)
     * 
     */
    @JsonProperty("event_no")
    @JsonPropertyDescription("The active or passive series number of this result (matches reported state values)")
    public Integer event_no;
    /**
     * Links to other address families (family and id)
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Links to other address families (family and id)")
    public Map<String, FamilyDiscovery> families;
    /**
     * Registry discovery results.
     * 
     */
    @JsonProperty("registries")
    @JsonPropertyDescription("Registry discovery results.")
    public Map<String, CloudModel> registries;
    /**
     * Device iot discovery scan results.
     * 
     */
    @JsonProperty("devices")
    @JsonPropertyDescription("Device iot discovery scan results.")
    public Map<String, CloudModel> devices;
    /**
     * Information about a specific point name of the device.
     * 
     */
    @JsonProperty("points")
    @JsonPropertyDescription("Information about a specific point name of the device.")
    public HashMap<String, PointPointsetModel> points;
    /**
     * Collection of point references discovered
     * 
     */
    @JsonProperty("refs")
    @JsonPropertyDescription("Collection of point references discovered")
    public Map<String, RefDiscovery> refs;
    /**
     * Discovery of features supported by this device.
     * 
     */
    @JsonProperty("features")
    @JsonPropertyDescription("Discovery of features supported by this device.")
    public Map<String, FeatureDiscovery> features;
    /**
     * Cloud Model
     * <p>
     * Information specific to how the device communicates with the cloud.
     * 
     */
    @JsonProperty("cloud_model")
    @JsonPropertyDescription("Information specific to how the device communicates with the cloud.")
    public udmi.schema.CloudModel cloud_model;
    /**
     * System Discovery Data
     * <p>
     * 
     * 
     */
    @JsonProperty("system")
    public SystemDiscoveryData system;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.registries == null)? 0 :this.registries.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        result = ((result* 31)+((this.features == null)? 0 :this.features.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.event_no == null)? 0 :this.event_no.hashCode()));
        result = ((result* 31)+((this.refs == null)? 0 :this.refs.hashCode()));
        result = ((result* 31)+((this.scan_family == null)? 0 :this.scan_family.hashCode()));
        result = ((result* 31)+((this.cloud_model == null)? 0 :this.cloud_model.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        result = ((result* 31)+((this.scan_addr == null)? 0 :this.scan_addr.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryEvents) == false) {
            return false;
        }
        DiscoveryEvents rhs = ((DiscoveryEvents) other);
        return ((((((((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.registries == rhs.registries)||((this.registries!= null)&&this.registries.equals(rhs.registries))))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))))&&((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features))))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))))&&((this.event_no == rhs.event_no)||((this.event_no!= null)&&this.event_no.equals(rhs.event_no))))&&((this.refs == rhs.refs)||((this.refs!= null)&&this.refs.equals(rhs.refs))))&&((this.scan_family == rhs.scan_family)||((this.scan_family!= null)&&this.scan_family.equals(rhs.scan_family))))&&((this.cloud_model == rhs.cloud_model)||((this.cloud_model!= null)&&this.cloud_model.equals(rhs.cloud_model))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))))&&((this.scan_addr == rhs.scan_addr)||((this.scan_addr!= null)&&this.scan_addr.equals(rhs.scan_addr))));
    }

}
