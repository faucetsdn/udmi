
package udmi.schema;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Link Externals Model
 * <p>
 * Information about how this device links to a specific external model
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "ext_id",
    "type",
    "etag",
    "label",
    "connections",
    "links"
})
public class LinkExternalsModel {

    /**
     * Id of this device in the external model namespace
     * 
     */
    @JsonProperty("ext_id")
    @JsonPropertyDescription("Id of this device in the external model namespace")
    public java.lang.String ext_id;
    /**
     * Type of this device in the external model namespace
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Type of this device in the external model namespace")
    public java.lang.String type;
    /**
     * Etag for referencing this external entity
     * 
     */
    @JsonProperty("etag")
    @JsonPropertyDescription("Etag for referencing this external entity")
    public java.lang.String etag;
    /**
     * Descriptive label for this entity
     * 
     */
    @JsonProperty("label")
    @JsonPropertyDescription("Descriptive label for this entity")
    public java.lang.String label;
    /**
     * Entity connections, keyed by guid
     *
     */
    @JsonProperty("connections")
    @JsonPropertyDescription("Entity connections, keyed by guid")
    public Map<String, Object> connections;
    /**
     * Virtual Equipment Links
     * <p>
     * Virtual equipment mapping, keyed by guid
     *
     */
    @JsonProperty("links")
    @JsonPropertyDescription("Virtual equipment mapping, keyed by guid")
    public VirtualEquipmentLinks links;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.etag == null)? 0 :this.etag.hashCode()));
        result = ((result* 31)+((this.links == null)? 0 :this.links.hashCode()));
        result = ((result* 31)+((this.ext_id == null)? 0 :this.ext_id.hashCode()));
        result = ((result* 31)+((this.label == null)? 0 :this.label.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.connections == null)? 0 :this.connections.hashCode()));
        return result;
    }

    @Override
    public boolean equals(java.lang.Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LinkExternalsModel) == false) {
            return false;
        }
        LinkExternalsModel rhs = ((LinkExternalsModel) other);
        return (((((((this.etag == rhs.etag)||((this.etag!= null)&&this.etag.equals(rhs.etag)))&&((this.links == rhs.links)||((this.links!= null)&&this.links.equals(rhs.links))))&&((this.ext_id == rhs.ext_id)||((this.ext_id!= null)&&this.ext_id.equals(rhs.ext_id))))&&((this.label == rhs.label)||((this.label!= null)&&this.label.equals(rhs.label))))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.connections == rhs.connections)||((this.connections!= null)&&this.connections.equals(rhs.connections))));
    }

}
