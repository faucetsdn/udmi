
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Common
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "family"
})
@Generated("jsonschema2pojo")
public class Common {

    /**
     * Protocol Family
     * <p>
     * 
     * 
     */
    @JsonProperty("family")
    public Common.ProtocolFamily family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Common) == false) {
            return false;
        }
        Common rhs = ((Common) other);
        return ((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family)));
    }


    /**
     * Protocol Family
     * <p>
     * 
     * 
     */
    @Generated("jsonschema2pojo")
    public enum ProtocolFamily {

        INVALID("invalid"),
        VENDOR("vendor"),
        IOT("iot"),
        ETHER("ether"),
        IPV_4("ipv4"),
        IPV_6("ipv6"),
        BACNET("bacnet"),
        MODBUS("modbus");
        private final String value;
        private final static Map<String, Common.ProtocolFamily> CONSTANTS = new HashMap<String, Common.ProtocolFamily>();

        static {
            for (Common.ProtocolFamily c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        ProtocolFamily(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Common.ProtocolFamily fromValue(String value) {
            Common.ProtocolFamily constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
