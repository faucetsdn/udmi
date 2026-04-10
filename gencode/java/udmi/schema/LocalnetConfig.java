
package udmi.schema;

import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Localnet Config
 * <p>
 * Currently unused: request local network configuration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families",
    "networks"
})
public class LocalnetConfig {

    /**
     * Address family config for reporting.
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Address family config for reporting.")
    public HashMap<String, FamilyLocalnetConfig> families;
    /**
     * Network address family config for reporting.
     *
     */
    @JsonProperty("networks")
    @JsonPropertyDescription("Network address family config for reporting.")
    public HashMap<String, FamilyLocalnetConfig> networks;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LocalnetConfig) == false) {
            return false;
        }
        LocalnetConfig rhs = ((LocalnetConfig) other);
        return (((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families)))&&((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks))));
    }

}
