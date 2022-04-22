
package udmi.schema;

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
    "enumeration",
    "families"
})
@Generated("jsonschema2pojo")
public class DiscoveryState {

    /**
     * Family Discovery State
     * <p>
     * State for [discovery](../docs/specs/discovery.md)
     * 
     */
    @JsonProperty("enumeration")
    @JsonPropertyDescription("State for [discovery](../docs/specs/discovery.md)")
    public udmi.schema.FamilyDiscoveryState enumeration;
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
        result = ((result* 31)+((this.enumeration == null)? 0 :this.enumeration.hashCode()));
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
        return (((this.enumeration == rhs.enumeration)||((this.enumeration!= null)&&this.enumeration.equals(rhs.enumeration)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))));
    }

}
