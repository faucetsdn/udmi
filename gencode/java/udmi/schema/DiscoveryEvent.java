
package udmi.schema;

import java.util.Date;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.udmi.util.Features;


/**
 * Discovery Event
 * <p>
 * [Discovery result](../docs/specs/discovery.md) with implicit enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "generation",
    "status",
    "scan_network",
    "scan_id",
    "networks",
    "uniqs",
    "features",
    "system"
})
@Generated("jsonschema2pojo")
public class DiscoveryEvent {

    /**
     * RFC 3339 timestamp the discover telemetry event was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the discover telemetry event was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * The event's discovery scan trigger's generation timestamp
     * (Required)
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
    /**
     * The primary discovery scan network
     * 
     */
    @JsonProperty("scan_network")
    @JsonPropertyDescription("The primary discovery scan network")
    public java.lang.String scan_network;
    /**
     * The primary id of the device (on scan_network)
     * 
     */
    @JsonProperty("scan_id")
    @JsonPropertyDescription("The primary id of the device (on scan_network)")
    public java.lang.String scan_id;
    /**
     * Discovery results by network
     * 
     */
    @JsonProperty("networks")
    @JsonPropertyDescription("Discovery results by network")
    public Map<String, NetworkDiscoveryEvent> networks;
    /**
     * Collection of unique data points available for this device.
     * 
     */
    @JsonProperty("uniqs")
    @JsonPropertyDescription("Collection of unique data points available for this device.")
    public Map<String, PointEnumerationEvent> uniqs;
    /**
     * UDMI Features Supported by this device
     * 
     */
    @JsonProperty("features")
    @JsonPropertyDescription("UDMI Features Supported by this device")
    public Features features;
    /**
     * System Discovery Event
     * <p>
     * 
     * 
     */
    @JsonProperty("system")
    public SystemDiscoveryEvent system;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.features == null)? 0 :this.features.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.scan_network == null)? 0 :this.scan_network.hashCode()));
        result = ((result* 31)+((this.uniqs == null)? 0 :this.uniqs.hashCode()));
        result = ((result* 31)+((this.scan_id == null)? 0 :this.scan_id.hashCode()));
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryEvent) == false) {
            return false;
        }
        DiscoveryEvent rhs = ((DiscoveryEvent) other);
        return (((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features))))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))))&&((this.scan_network == rhs.scan_network)||((this.scan_network!= null)&&this.scan_network.equals(rhs.scan_network))))&&((this.uniqs == rhs.uniqs)||((this.uniqs!= null)&&this.uniqs.equals(rhs.uniqs))))&&((this.scan_id == rhs.scan_id)||((this.scan_id!= null)&&this.scan_id.equals(rhs.scan_id))))&&((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
