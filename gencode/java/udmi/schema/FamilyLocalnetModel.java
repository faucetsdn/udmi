
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Localnet Model
 * <p>
 * The type of network
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr"
})
@Generated("jsonschema2pojo")
public class FamilyLocalnetModel {

    /**
     * The address of a device on the local network
     * (Required)
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("The address of a device on the local network")
    public String addr;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyLocalnetModel) == false) {
            return false;
        }
        FamilyLocalnetModel rhs = ((FamilyLocalnetModel) other);
        return ((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)));
    }

}
