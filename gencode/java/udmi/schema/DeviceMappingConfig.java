
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Device Mapping Config
 * <p>
 * Configuration for [mapping](../docs/specs/mapping.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "guid",
    "applied",
    "requested",
    "status"
})
public class DeviceMappingConfig {

    /**
     * Device guid
     * 
     */
    @JsonProperty("guid")
    @JsonPropertyDescription("Device guid")
    public String guid;
    /**
     * Last time the mapping was successfully applied for this device
     * 
     */
    @JsonProperty("applied")
    @JsonPropertyDescription("Last time the mapping was successfully applied for this device")
    public Date applied;
    /**
     * Timestamp of requested device model export
     * 
     */
    @JsonProperty("requested")
    @JsonPropertyDescription("Timestamp of requested device model export")
    public Date requested;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.guid == null)? 0 :this.guid.hashCode()));
        result = ((result* 31)+((this.requested == null)? 0 :this.requested.hashCode()));
        result = ((result* 31)+((this.applied == null)? 0 :this.applied.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DeviceMappingConfig) == false) {
            return false;
        }
        DeviceMappingConfig rhs = ((DeviceMappingConfig) other);
        return (((((this.guid == rhs.guid)||((this.guid!= null)&&this.guid.equals(rhs.guid)))&&((this.requested == rhs.requested)||((this.requested!= null)&&this.requested.equals(rhs.requested))))&&((this.applied == rhs.applied)||((this.applied!= null)&&this.applied.equals(rhs.applied))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
