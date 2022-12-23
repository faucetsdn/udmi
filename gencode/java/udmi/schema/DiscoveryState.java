
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery State
 * <p>
 * State for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "families"
})
@Generated("jsonschema2pojo")
public class DiscoveryState {

    /**
     * Generational marker for enumeration
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for enumeration")
    public Date generation;
    /**
     * Discovery protocol families
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Discovery protocol families")
    public HashMap<String, FamilyDiscoveryState> families;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryState) == false) {
            return false;
        }
        DiscoveryState rhs = ((DiscoveryState) other);
        return (((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))));
    }

}
