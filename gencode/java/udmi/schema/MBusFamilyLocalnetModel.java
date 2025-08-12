
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * MBus Family Localnet Model
 * <p>
 * The type of network
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
    "mbus_adjunct"
})
public class MBusFamilyLocalnetModel {

    /**
     * The address of a device on the fieldbus/local network
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("The address of a device on the fieldbus/local network")
    public String addr;
    @JsonProperty("mbus_adjunct")
    public Mbus_adjunct mbus_adjunct;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.mbus_adjunct == null)? 0 :this.mbus_adjunct.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MBusFamilyLocalnetModel) == false) {
            return false;
        }
        MBusFamilyLocalnetModel rhs = ((MBusFamilyLocalnetModel) other);
        return (((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.mbus_adjunct == rhs.mbus_adjunct)||((this.mbus_adjunct!= null)&&this.mbus_adjunct.equals(rhs.mbus_adjunct))));
    }

}
