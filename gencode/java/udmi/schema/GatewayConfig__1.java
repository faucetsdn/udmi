
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
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
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class GatewayConfig__1 {

    /**
     * An array of all the device IDs which are bound to the device
     * (Required)
     * 
     */
    @JsonProperty("proxy_ids")
    @JsonPropertyDescription("An array of all the device IDs which are bound to the device")
    public List<String> proxy_ids = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.proxy_ids == null)? 0 :this.proxy_ids.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayConfig__1) == false) {
            return false;
        }
        GatewayConfig__1 rhs = ((GatewayConfig__1) other);
        return ((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids)));
    }

}
