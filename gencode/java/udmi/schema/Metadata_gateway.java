
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Gateway metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "gateway_id",
    "subsystem",
    "proxy_ids"
})
@Generated("jsonschema2pojo")
public class Metadata_gateway {

    @JsonProperty("gateway_id")
    public String gateway_id;
    @JsonProperty("subsystem")
    public String subsystem;
    @JsonProperty("proxy_ids")
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
        if ((other instanceof Metadata_gateway) == false) {
            return false;
        }
        Metadata_gateway rhs = ((Metadata_gateway) other);
        return ((((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)))&&((this.proxy_ids == rhs.proxy_ids)||((this.proxy_ids!= null)&&this.proxy_ids.equals(rhs.proxy_ids))))&&((this.gateway_id == rhs.gateway_id)||((this.gateway_id!= null)&&this.gateway_id.equals(rhs.gateway_id))));
    }

}
