
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
 * Point Pointset State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "value_state",
    "status"
})
@Generated("jsonschema2pojo")
public class PointPointsetState__1 {

    @JsonProperty("value_state")
    public PointPointsetState__1 .Value_state value_state;
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.value_state == null)? 0 :this.value_state.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointPointsetState__1) == false) {
            return false;
        }
        PointPointsetState__1 rhs = ((PointPointsetState__1) other);
        return (((this.value_state == rhs.value_state)||((this.value_state!= null)&&this.value_state.equals(rhs.value_state)))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

    @Generated("jsonschema2pojo")
    public enum Value_state {

        APPLIED("applied"),
        UPDATING("updating"),
        OVERRIDDEN("overridden"),
        INVALID("invalid"),
        FAILURE("failure");
        private final String value;
        private final static Map<String, PointPointsetState__1 .Value_state> CONSTANTS = new HashMap<String, PointPointsetState__1 .Value_state>();

        static {
            for (PointPointsetState__1 .Value_state c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Value_state(String value) {
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
        public static PointPointsetState__1 .Value_state fromValue(String value) {
            PointPointsetState__1 .Value_state constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
