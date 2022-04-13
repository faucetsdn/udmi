
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway State
 * <p>
 * [Gateway Documentation](../docs/specs/gateway.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "devices"
})
@Generated("jsonschema2pojo")
public class GatewayState {

    @JsonProperty("devices")
    public Object devices;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayState) == false) {
            return false;
        }
        GatewayState rhs = ((GatewayState) other);
        return ((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices)));
    }

}
