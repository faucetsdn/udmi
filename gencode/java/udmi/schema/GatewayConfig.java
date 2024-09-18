
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway Config
 * <p>
 * Configuration for gateways. Only required for devices which are acting as [gateways](../docs/specs/gateway.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "proxy_ids",
    "target"
})
public class GatewayConfig {

    /**
     * An array of all the device IDs which are bound to the device
     * 
     */
    @JsonProperty("proxy_ids")
    @JsonPropertyDescription("An array of all the device IDs which are bound to the device")
    public List<String> proxy_ids = new ArrayList<String>();
    /**
     * Family Localnet Model
     * <p>
     * The type of network
     * 
     */
    @JsonProperty("target")
    @JsonPropertyDescription("The type of network")
    public FamilyLocalnetModel target;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.proxy_ids == null)? 0 :this.proxy_ids.hashCode()));
        result = ((result* 31)+((this.target == null)? 0 :this.target.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayConfig) == false) {
            return false;
        }
        GatewayConfig rhs = ((GatewayConfig) other);
        return (((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids)))&&((this.target == rhs.target)||((this.target!= null)&&this.target.equals(rhs.target))));
    }

}
