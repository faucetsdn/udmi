
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * BACnet Family Localnet Model
 * <p>
 * The type of network
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
    "bacnet_adjunct"
})
public class BACnetFamilyLocalnetModel {

    /**
     * The address of a device on the fieldbus/local network
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("The address of a device on the fieldbus/local network")
    public String addr;
    @JsonProperty("bacnet_adjunct")
    public Bacnet_adjunct bacnet_adjunct;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.bacnet_adjunct == null)? 0 :this.bacnet_adjunct.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BACnetFamilyLocalnetModel) == false) {
            return false;
        }
        BACnetFamilyLocalnetModel rhs = ((BACnetFamilyLocalnetModel) other);
        return (((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.bacnet_adjunct == rhs.bacnet_adjunct)||((this.bacnet_adjunct!= null)&&this.bacnet_adjunct.equals(rhs.bacnet_adjunct))));
    }

}
