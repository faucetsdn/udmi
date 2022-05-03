
package udmi.schema;

import java.util.Date;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


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
    "scan_family",
    "families",
    "points"
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
     * The primary scan discovery address family
     * 
     */
    @JsonProperty("scan_family")
    @JsonPropertyDescription("The primary scan discovery address family")
    public java.lang.String scan_family;
    /**
     * Address family results for a scan. Not included for device enumeration messages.
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Address family results for a scan. Not included for device enumeration messages.")
    public Map<String, FamilyDiscoveryEvent> families;
    /**
     * Collection of data points available for this device.
     * 
     */
    @JsonProperty("points")
    @JsonPropertyDescription("Collection of data points available for this device.")
    public Map<String, PointEnumerationEvent> points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.scan_family == null)? 0 :this.scan_family.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
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
        return ((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.scan_family == rhs.scan_family)||((this.scan_family!= null)&&this.scan_family.equals(rhs.scan_family))))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

}
