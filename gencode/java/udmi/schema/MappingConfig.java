
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Mapping Config
 * <p>
 * Configuration for [mapping](../docs/specs/mapping.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "devices",
    "extras_deletion_days",
    "devices_deletion_days"
})
public class MappingConfig {

    /**
     * RFC 3339 timestamp the configuration was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the configuration was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Configuration of mapped devices
     * 
     */
    @JsonProperty("devices")
    @JsonPropertyDescription("Configuration of mapped devices")
    public HashMap<String, DeviceMappingConfig> devices;
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
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MappingConfig) == false) {
            return false;
        }
        MappingConfig rhs = ((MappingConfig) other);
        return ((((((this.extras_deletion_days == rhs.extras_deletion_days)||((this.extras_deletion_days!= null)&&this.extras_deletion_days.equals(rhs.extras_deletion_days)))&&((this.devices_deletion_days == rhs.devices_deletion_days)||((this.devices_deletion_days!= null)&&this.devices_deletion_days.equals(rhs.devices_deletion_days))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
