
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "serial_port",
    "bit_rate",
    "secondary_addr"
})
public class Mbus_adjunct {

    @JsonProperty("serial_port")
    public String serial_port;
    @JsonProperty("bit_rate")
    public Integer bit_rate;
    @JsonProperty("secondary_addr")
    public String secondary_addr;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.secondary_addr == null)? 0 :this.secondary_addr.hashCode()));
        result = ((result* 31)+((this.serial_port == null)? 0 :this.serial_port.hashCode()));
        result = ((result* 31)+((this.bit_rate == null)? 0 :this.bit_rate.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Mbus_adjunct) == false) {
            return false;
        }
        Mbus_adjunct rhs = ((Mbus_adjunct) other);
        return ((((this.secondary_addr == rhs.secondary_addr)||((this.secondary_addr!= null)&&this.secondary_addr.equals(rhs.secondary_addr)))&&((this.serial_port == rhs.serial_port)||((this.serial_port!= null)&&this.serial_port.equals(rhs.serial_port))))&&((this.bit_rate == rhs.bit_rate)||((this.bit_rate!= null)&&this.bit_rate.equals(rhs.bit_rate))));
    }

}
