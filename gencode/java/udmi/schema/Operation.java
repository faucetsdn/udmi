
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "mode"
})
@Generated("jsonschema2pojo")
public class Operation {

    /**
     * System Mode
     * <p>
     * Operating mode for the device. Default is 'active'.
     * 
     */
    @JsonProperty("mode")
    @JsonPropertyDescription("Operating mode for the device. Default is 'active'.")
    public Operation.SystemMode mode;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.mode == null)? 0 :this.mode.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Operation) == false) {
            return false;
        }
        Operation rhs = ((Operation) other);
        return ((this.mode == rhs.mode)||((this.mode!= null)&&this.mode.equals(rhs.mode)));
    }


    /**
     * System Mode
     * <p>
     * Operating mode for the device. Default is 'active'.
     * 
     */
    @Generated("jsonschema2pojo")
    public enum SystemMode {

        INITIAL("initial"),
        ACTIVE("active"),
        RESTART("restart"),
        SHUTDOWN("shutdown");
        private final String value;
        private final static Map<String, Operation.SystemMode> CONSTANTS = new HashMap<String, Operation.SystemMode>();

        static {
            for (Operation.SystemMode c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SystemMode(String value) {
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
        public static Operation.SystemMode fromValue(String value) {
            Operation.SystemMode constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
