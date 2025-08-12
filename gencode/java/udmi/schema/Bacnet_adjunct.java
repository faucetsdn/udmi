
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "description"
})
public class Bacnet_adjunct {

    /**
     * The name of the BACnet device.
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("The name of the BACnet device.")
    public String name;
    /**
     * A description for the BACnet device.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A description for the BACnet device.")
    public String description;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Bacnet_adjunct) == false) {
            return false;
        }
        Bacnet_adjunct rhs = ((Bacnet_adjunct) other);
        return (((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name)))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))));
    }

}
