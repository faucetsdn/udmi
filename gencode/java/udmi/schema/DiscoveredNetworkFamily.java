
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovered Network Family
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
    "scope"
})
@Generated("jsonschema2pojo")
public class DiscoveredNetworkFamily {

    @JsonProperty("addr")
    public String addr;
    @JsonProperty("scope")
    public String scope;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.scope == null)? 0 :this.scope.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveredNetworkFamily) == false) {
            return false;
        }
        DiscoveredNetworkFamily rhs = ((DiscoveredNetworkFamily) other);
        return (((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.scope == rhs.scope)||((this.scope!= null)&&this.scope.equals(rhs.scope))));
    }

}
