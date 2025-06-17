
package udmi.schema;

import java.util.Date;
import java.util.Map;
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
    "enumerations",
    "families"
})
public class DiscoveryConfig {

    /**
     * Generational marker for controlling self-enumeration
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for controlling self-enumeration")
    public Date generation;
    /**
     * Enumeration depth for self-enumerations.
     * 
     */
    @JsonProperty("enumerations")
    @JsonPropertyDescription("Enumeration depth for self-enumerations.")
    public Enumerations enumerations;
    /**
     * Address family configs for discovery scans.
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Address family configs for discovery scans.")
    public Map<String, FamilyDiscoveryConfig> families;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.enumerations == null)? 0 :this.enumerations.hashCode()));
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
        return ((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))))&&((this.enumerations == rhs.enumerations)||((this.enumerations!= null)&&this.enumerations.equals(rhs.enumerations))));
    }

}
