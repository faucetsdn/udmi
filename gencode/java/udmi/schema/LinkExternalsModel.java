
package udmi.schema;

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
    "entity_id",
    "entity_type",
    "description"
})
public class LinkExternalsModel {

    /**
     * Id of this device in the external model namespace
     * 
     */
    @JsonProperty("entity_id")
    @JsonPropertyDescription("Id of this device in the external model namespace")
    public String entity_id;
    /**
     * Type of this device in the external model namespace
     * 
     */
    @JsonProperty("entity_type")
    @JsonPropertyDescription("Type of this device in the external model namespace")
    public String entity_type;
    /**
     * Description of this device in the external model namespace
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Description of this device in the external model namespace")
    public String description;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.entity_id == null)? 0 :this.entity_id.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.entity_type == null)? 0 :this.entity_type.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LinkExternalsModel) == false) {
            return false;
        }
        LinkExternalsModel rhs = ((LinkExternalsModel) other);
        return ((((this.entity_id == rhs.entity_id)||((this.entity_id!= null)&&this.entity_id.equals(rhs.entity_id)))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.entity_type == rhs.entity_type)||((this.entity_type!= null)&&this.entity_type.equals(rhs.entity_type))));
    }

}
