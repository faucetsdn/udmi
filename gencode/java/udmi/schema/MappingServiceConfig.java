
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Mapping Service Config
 * <p>
 * Mapping Service Configuration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "extras_deletion_days",
    "devices_deletion_days"
})
public class MappingServiceConfig {

    /**
     * extras discovery event garbage collection time
     * 
     */
    @JsonProperty("extras_deletion_days")
    @JsonPropertyDescription("extras discovery event garbage collection time")
    public Integer extras_deletion_days;
    /**
     * devices garbage collection time
     * 
     */
    @JsonProperty("devices_deletion_days")
    @JsonPropertyDescription("devices garbage collection time")
    public Integer devices_deletion_days;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.extras_deletion_days == null)? 0 :this.extras_deletion_days.hashCode()));
        result = ((result* 31)+((this.devices_deletion_days == null)? 0 :this.devices_deletion_days.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MappingServiceConfig) == false) {
            return false;
        }
        MappingServiceConfig rhs = ((MappingServiceConfig) other);
        return (((this.extras_deletion_days == rhs.extras_deletion_days)||((this.extras_deletion_days!= null)&&this.extras_deletion_days.equals(rhs.extras_deletion_days)))&&((this.devices_deletion_days == rhs.devices_deletion_days)||((this.devices_deletion_days!= null)&&this.devices_deletion_days.equals(rhs.devices_deletion_days))));
    }

}
