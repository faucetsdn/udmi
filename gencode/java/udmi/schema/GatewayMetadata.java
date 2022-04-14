
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway Metadata
 * <p>
 * [Gateway Documentation](../docs/specs/gateway.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "gateway_id",
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class GatewayMetadata {

    /**
     * The device ID of the gateway the device is bound to
     * 
     */
    @JsonProperty("gateway_id")
    @JsonPropertyDescription("The device ID of the gateway the device is bound to")
    public String gateway_id;
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
        result = ((result* 31)+((this.gateway_id == null)? 0 :this.gateway_id.hashCode()));
        result = ((result* 31)+((this.proxy_ids == null)? 0 :this.proxy_ids.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayMetadata) == false) {
            return false;
        }
        GatewayMetadata rhs = ((GatewayMetadata) other);
        return (((this.gateway_id == rhs.gateway_id)||((this.gateway_id!= null)&&this.gateway_id.equals(rhs.gateway_id)))&&((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids))));
    }

}
