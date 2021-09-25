
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway State
 * <p>
 * Read more: <https://github.com/faucetsdn/udmi/blob/master/docs/gateway.md>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "error_ids"
})
@Generated("jsonschema2pojo")
public class GatewayState__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("error_ids")
    public List<String> error_ids = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.error_ids == null)? 0 :this.error_ids.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GatewayState__1) == false) {
            return false;
        }
        GatewayState__1 rhs = ((GatewayState__1) other);
        return ((this.error_ids == rhs.error_ids)||((this.error_ids!= null)&&this.error_ids.equals(rhs.error_ids)));
    }

}
