
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "serial_port",
    "bit_rate",
    "data_bits",
    "stop_bits",
    "parity",
    "encoding"
})
public class Modbus_adjunct {

    @JsonProperty("serial_port")
    public String serial_port;
    @JsonProperty("bit_rate")
    public Integer bit_rate;
    @JsonProperty("data_bits")
    public String data_bits;
    @JsonProperty("stop_bits")
    public String stop_bits;
    @JsonProperty("parity")
    public String parity;
    @JsonProperty("encoding")
    public String encoding;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.bit_rate == null)? 0 :this.bit_rate.hashCode()));
        result = ((result* 31)+((this.stop_bits == null)? 0 :this.stop_bits.hashCode()));
        result = ((result* 31)+((this.data_bits == null)? 0 :this.data_bits.hashCode()));
        result = ((result* 31)+((this.encoding == null)? 0 :this.encoding.hashCode()));
        result = ((result* 31)+((this.serial_port == null)? 0 :this.serial_port.hashCode()));
        result = ((result* 31)+((this.parity == null)? 0 :this.parity.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Modbus_adjunct) == false) {
            return false;
        }
        Modbus_adjunct rhs = ((Modbus_adjunct) other);
        return (((((((this.bit_rate == rhs.bit_rate)||((this.bit_rate!= null)&&this.bit_rate.equals(rhs.bit_rate)))&&((this.stop_bits == rhs.stop_bits)||((this.stop_bits!= null)&&this.stop_bits.equals(rhs.stop_bits))))&&((this.data_bits == rhs.data_bits)||((this.data_bits!= null)&&this.data_bits.equals(rhs.data_bits))))&&((this.encoding == rhs.encoding)||((this.encoding!= null)&&this.encoding.equals(rhs.encoding))))&&((this.serial_port == rhs.serial_port)||((this.serial_port!= null)&&this.serial_port.equals(rhs.serial_port))))&&((this.parity == rhs.parity)||((this.parity!= null)&&this.parity.equals(rhs.parity))));
    }

}
