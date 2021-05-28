
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
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "error_ids"
})
@Generated("jsonschema2pojo")
public class GatewayState {

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
        if ((other instanceof GatewayState) == false) {
            return false;
        }
        GatewayState rhs = ((GatewayState) other);
        return ((this.error_ids == rhs.error_ids)||((this.error_ids!= null)&&this.error_ids.equals(rhs.error_ids)));
    }

}
