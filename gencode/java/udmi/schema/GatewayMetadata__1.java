
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
 * Read more: <https://github.com/faucetsdn/udmi/blob/master/docs/gateway.md>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "gateway_id",
    "subsystem",
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class GatewayMetadata__1 {

    /**
     * Present in devices which are proxied by a gateway, this identifies the device ID of the gateway the device is bound to
     * 
     */
    @JsonProperty("gateway_id")
    @JsonPropertyDescription("Present in devices which are proxied by a gateway, this identifies the device ID of the gateway the device is bound to")
    public String gateway_id;
    @JsonProperty("subsystem")
    public String subsystem;
    /**
     * Present in devices which are IoT gateways, this is an array of all the device IDs which are bound to the device
     * 
     */
    @JsonProperty("proxy_ids")
    @JsonPropertyDescription("Present in devices which are IoT gateways, this is an array of all the device IDs which are bound to the device")
    public List<String> proxy_ids = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.subsystem == null)? 0 :this.subsystem.hashCode()));
        result = ((result* 31)+((this.proxy_ids == null)? 0 :this.proxy_ids.hashCode()));
        result = ((result* 31)+((this.gateway_id == null)? 0 :this.gateway_id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayMetadata__1) == false) {
            return false;
        }
        GatewayMetadata__1 rhs = ((GatewayMetadata__1) other);
        return ((((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)))&&((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids))))&&((this.gateway_id == rhs.gateway_id)||((this.gateway_id!= null)&&this.gateway_id.equals(rhs.gateway_id))));
    }

}
