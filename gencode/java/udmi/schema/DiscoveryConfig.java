
package udmi.schema;

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
    "enumeration",
    "families"
})
@Generated("jsonschema2pojo")
public class DiscoveryConfig {

    /**
     * Family Discovery Config
     * <p>
     * Configuration for [discovery](../docs/specs/discovery.md)
     * 
     */
    @JsonProperty("enumeration")
    @JsonPropertyDescription("Configuration for [discovery](../docs/specs/discovery.md)")
    public FamilyDiscoveryConfig enumeration;
    /**
     * Address family results for a scan. Not included for device enumeration messages.
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Address family results for a scan. Not included for device enumeration messages.")
    public Object families;

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
        if ((other instanceof DiscoveryConfig) == false) {
            return false;
        }
        DiscoveryConfig rhs = ((DiscoveryConfig) other);
        return (((this.enumeration == rhs.enumeration)||((this.enumeration!= null)&&this.enumeration.equals(rhs.enumeration)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))));
    }

}
