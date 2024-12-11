
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Enumeration depth for self-enumerations.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families",
    "devices",
    "points",
    "features"
})
public class Enumerations {

    @JsonProperty("families")
    public Enumerations.Depth families;
    @JsonProperty("devices")
    public Enumerations.Depth devices;
    @JsonProperty("points")
    public Enumerations.Depth points;
    @JsonProperty("features")
    public Enumerations.Depth features;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.features == null)? 0 :this.features.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.devices == null)? 0 :this.devices.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Enumerations) == false) {
            return false;
        }
        Enumerations rhs = ((Enumerations) other);
        return (((((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

    public enum Depth {

        BUCKETS("buckets"),
        ENTRIES("entries"),
        DETAILS("details"),
        PARTS("parts");
        private final String value;
        private final static Map<String, Enumerations.Depth> CONSTANTS = new HashMap<String, Enumerations.Depth>();

        static {
            for (Enumerations.Depth c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Depth(String value) {
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
        public static Enumerations.Depth fromValue(String value) {
            Enumerations.Depth constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
