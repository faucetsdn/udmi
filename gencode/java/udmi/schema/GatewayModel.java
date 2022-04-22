
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway Model
 * <p>
 * [Gateway Documentation](../docs/specs/gateway.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "gateway_id",
    "family",
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class GatewayModel {

    /**
     * The device ID of the gateway the device is bound to
     * 
     */
    @JsonProperty("gateway_id")
    @JsonPropertyDescription("The device ID of the gateway the device is bound to")
    public String gateway_id;
    /**
     * Protocol family used for connecting to the proxy device
     * 
     */
    @JsonProperty("family")
    @JsonPropertyDescription("Protocol family used for connecting to the proxy device")
    public String family;
    /**
     * An array of all the device IDs which are bound to the device
     * 
     */
    @JsonProperty("proxy_ids")
    @JsonPropertyDescription("An array of all the device IDs which are bound to the device")
    public List<String> proxy_ids = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.proxy_ids == null)? 0 :this.proxy_ids.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        result = ((result* 31)+((this.gateway_id == null)? 0 :this.gateway_id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayModel) == false) {
            return false;
        }
        GatewayModel rhs = ((GatewayModel) other);
        return ((((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids)))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))))&&((this.gateway_id == rhs.gateway_id)||((this.gateway_id!= null)&&this.gateway_id.equals(rhs.gateway_id))));
    }

}
