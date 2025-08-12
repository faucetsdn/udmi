
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Modbus Family Localnet Model
 * <p>
 * The type of network
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr",
    "modbus_adjunct"
})
public class ModbusFamilyLocalnetModel {

    /**
     * The address of a device on the fieldbus/local network
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("The address of a device on the fieldbus/local network")
    public String addr;
    @JsonProperty("modbus_adjunct")
    public Modbus_adjunct modbus_adjunct;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.modbus_adjunct == null)? 0 :this.modbus_adjunct.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ModbusFamilyLocalnetModel) == false) {
            return false;
        }
        ModbusFamilyLocalnetModel rhs = ((ModbusFamilyLocalnetModel) other);
        return (((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.modbus_adjunct == rhs.modbus_adjunct)||((this.modbus_adjunct!= null)&&this.modbus_adjunct.equals(rhs.modbus_adjunct))));
    }

}
