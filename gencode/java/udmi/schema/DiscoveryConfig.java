
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
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
    "enumerate",
    "families"
})
@Generated("jsonschema2pojo")
public class DiscoveryConfig {

    /**
     * Generational marker for controlling enumeration
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for controlling enumeration")
    public Date generation;
    @JsonProperty("enumerate")
    public Enumerate enumerate;
    /**
     * Address family config for a scan. Not included for device enumeration messages.
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Address family config for a scan. Not included for device enumeration messages.")
    public HashMap<String, FamilyDiscoveryConfig> families;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.enumerate == null)? 0 :this.enumerate.hashCode()));
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
        return ((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.enumerate == rhs.enumerate)||((this.enumerate!= null)&&this.enumerate.equals(rhs.enumerate))))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))));
    }

}
