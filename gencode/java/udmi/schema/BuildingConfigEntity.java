
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Building Config Entity
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "description"
})
public class BuildingConfigEntity {

    @JsonProperty("description")
    public String description;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BuildingConfigEntity) == false) {
            return false;
        }
        BuildingConfigEntity rhs = ((BuildingConfigEntity) other);
        return ((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description)));
    }

}
