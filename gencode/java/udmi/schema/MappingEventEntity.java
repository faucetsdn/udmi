
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Mapping Event Entity
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "code",
    "type",
    "cloud_device_id",
    "connections",
    "links",
    "translation",
    "missing_telemetry_fields"
})
@Generated("jsonschema2pojo")
public class MappingEventEntity {

    /**
     * 
     */
    @JsonProperty("code")
    @JsonPropertyDescription("")
    public java.lang.String code;
    /**
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("")
    public java.lang.String type;
    /**
     * 
     */
    @JsonProperty("cloud_device_id")
    @JsonPropertyDescription("")
    public java.lang.String cloud_device_id;
    @JsonProperty("connections")
    public Object connections;
    @JsonProperty("links")
    public Object links;
    /**
     * Building Config
     * <p>
     * [Discovery result](../docs/specs/discovery.md) with implicit enumeration
     * 
     */
    @JsonProperty("translation")
    @JsonPropertyDescription("[Discovery result](../docs/specs/discovery.md) with implicit enumeration")
    public HashMap<String, BuildingTranslation> translation;
    @JsonProperty("missing_telemetry_fields")
    public Object missing_telemetry_fields;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.code == null)? 0 :this.code.hashCode()));
        result = ((result* 31)+((this.missing_telemetry_fields == null)? 0 :this.missing_telemetry_fields.hashCode()));
        result = ((result* 31)+((this.translation == null)? 0 :this.translation.hashCode()));
        result = ((result* 31)+((this.cloud_device_id == null)? 0 :this.cloud_device_id.hashCode()));
        result = ((result* 31)+((this.links == null)? 0 :this.links.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.connections == null)? 0 :this.connections.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MappingEventEntity) == false) {
            return false;
        }
        MappingEventEntity rhs = ((MappingEventEntity) other);
        return ((((((((this.code == rhs.code)||((this.code!= null)&&this.code.equals(rhs.code)))&&((this.missing_telemetry_fields == rhs.missing_telemetry_fields)||((this.missing_telemetry_fields!= null)&&this.missing_telemetry_fields.equals(rhs.missing_telemetry_fields))))&&((this.translation == rhs.translation)||((this.translation!= null)&&this.translation.equals(rhs.translation))))&&((this.cloud_device_id == rhs.cloud_device_id)||((this.cloud_device_id!= null)&&this.cloud_device_id.equals(rhs.cloud_device_id))))&&((this.links == rhs.links)||((this.links!= null)&&this.links.equals(rhs.links))))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.connections == rhs.connections)||((this.connections!= null)&&this.connections.equals(rhs.connections))));
    }

}
