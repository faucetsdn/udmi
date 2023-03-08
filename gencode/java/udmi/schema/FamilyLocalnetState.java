
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Localnet State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
<<<<<<< HEAD
    "scope",
=======
>>>>>>> master
    "status"
})
@Generated("jsonschema2pojo")
public class FamilyLocalnetState {

    @JsonProperty("addr")
    public String addr;
<<<<<<< HEAD
    @JsonProperty("scope")
    public String scope;
=======
>>>>>>> master
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
<<<<<<< HEAD
        result = ((result* 31)+((this.scope == null)? 0 :this.scope.hashCode()));
=======
>>>>>>> master
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyLocalnetState) == false) {
            return false;
        }
        FamilyLocalnetState rhs = ((FamilyLocalnetState) other);
<<<<<<< HEAD
        return ((((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.scope == rhs.scope)||((this.scope!= null)&&this.scope.equals(rhs.scope))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
=======
        return (((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
>>>>>>> master
    }

}
