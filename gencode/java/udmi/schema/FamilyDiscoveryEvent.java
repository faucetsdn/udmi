
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Discovery Event
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "group"
})
@Generated("jsonschema2pojo")
public class FamilyDiscoveryEvent {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    public String id;
    @JsonProperty("group")
    public String group;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        result = ((result* 31)+((this.group == null)? 0 :this.group.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyDiscoveryEvent) == false) {
            return false;
        }
        FamilyDiscoveryEvent rhs = ((FamilyDiscoveryEvent) other);
        return (((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id)))&&((this.group == rhs.group)||((this.group!= null)&&this.group.equals(rhs.group))));
    }

}
