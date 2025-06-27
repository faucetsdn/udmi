
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Events
 * <p>
 * Container object for all event schemas, not directly used.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "system",
    "pointset",
    "discovery"
})
public class Events {

    /**
     * System Events
     * <p>
     * Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)
     * 
     */
    @JsonProperty("system")
    @JsonPropertyDescription("Used for system events such as logging. [System Event Documentation](../docs/messages/system.md#event)")
    public SystemEvents system;
    /**
     * Pointset Events
     * <p>
     * A set of points reporting telemetry data. [Pointset Events Documentation](../docs/messages/pointset.md#telemetry)
     * 
     */
    @JsonProperty("pointset")
    @JsonPropertyDescription("A set of points reporting telemetry data. [Pointset Events Documentation](../docs/messages/pointset.md#telemetry)")
    public PointsetEvents pointset;
    /**
     * Discovery Events
     * <p>
     * [Discovery result](../docs/specs/discovery.md) with implicit discovery
     * 
     */
    @JsonProperty("discovery")
    @JsonPropertyDescription("[Discovery result](../docs/specs/discovery.md) with implicit discovery")
    public DiscoveryEvents discovery;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.pointset == null)? 0 :this.pointset.hashCode()));
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Events) == false) {
            return false;
        }
        Events rhs = ((Events) other);
        return ((((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset)))&&((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery))))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))));
    }

}
