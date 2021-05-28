
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway Config Snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class Config_gateway {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("proxy_ids")
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
        if ((other instanceof Config_gateway) == false) {
            return false;
        }
        Config_gateway rhs = ((Config_gateway) other);
        return ((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids)));
    }

}
