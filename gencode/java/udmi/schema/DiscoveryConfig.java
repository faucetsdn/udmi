
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery Config
 * <p>
 * Configuration for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "depths",
    "families"
})
public class DiscoveryConfig {

    /**
     * Generational marker for controlling enumeration
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for controlling enumeration")
    public Date generation;
    /**
     * Indicates which discovery sub-categories to activate
     * 
     */
    @JsonProperty("depths")
    @JsonPropertyDescription("Indicates which discovery sub-categories to activate")
    public Depths depths;
    /**
     * Address family config for a scan.
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Address family config for a scan.")
    public HashMap<String, FamilyDiscoveryConfig> families;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.depths == null)? 0 :this.depths.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryConfig) == false) {
            return false;
        }
        DiscoveryConfig rhs = ((DiscoveryConfig) other);
        return ((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.depths == rhs.depths)||((this.depths!= null)&&this.depths.equals(rhs.depths))))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))));
    }

}
