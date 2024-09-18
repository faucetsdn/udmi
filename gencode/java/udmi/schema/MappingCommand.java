
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Mapping Command
 * <p>
 * [Mapping command](../docs/specs/mapping.md) for provisioning
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "guid",
    "device_num_id",
    "status",
    "translation"
})
public class MappingCommand {

    /**
     * RFC 3339 UTC timestamp the configuration was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the configuration was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * device guid
     * 
     */
    @JsonProperty("guid")
    @JsonPropertyDescription("device guid")
    public java.lang.String guid;
    /**
     * numerical id assigned to this device
     * 
     */
    @JsonProperty("device_num_id")
    @JsonPropertyDescription("numerical id assigned to this device")
    public Double device_num_id;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    /**
     * Building Config
     * <p>
     * [Discovery result](../docs/specs/discovery.md) with implicit results
     * 
     */
    @JsonProperty("translation")
    @JsonPropertyDescription("[Discovery result](../docs/specs/discovery.md) with implicit results")
    public HashMap<String, BuildingTranslation> translation;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.device_num_id == null)? 0 :this.device_num_id.hashCode()));
        result = ((result* 31)+((this.translation == null)? 0 :this.translation.hashCode()));
        result = ((result* 31)+((this.guid == null)? 0 :this.guid.hashCode()));
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
        if ((other instanceof MappingCommand) == false) {
            return false;
        }
        MappingCommand rhs = ((MappingCommand) other);
        return (((((((this.device_num_id == rhs.device_num_id)||((this.device_num_id!= null)&&this.device_num_id.equals(rhs.device_num_id)))&&((this.translation == rhs.translation)||((this.translation!= null)&&this.translation.equals(rhs.translation))))&&((this.guid == rhs.guid)||((this.guid!= null)&&this.guid.equals(rhs.guid))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
