
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Indicates which discovery sub-categories to enumerate
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families",
    "devices",
    "points",
    "features"
})
public class Depths {

    @JsonProperty("families")
    public Depths.Depth families;
    @JsonProperty("devices")
    public Depths.Depth devices;
    @JsonProperty("points")
    public Depths.Depth points;
    @JsonProperty("features")
    public Depths.Depth features;

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
        if ((other instanceof Depths) == false) {
            return false;
        }
        Depths rhs = ((Depths) other);
        return (((((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))))&&((this.devices == rhs.devices)||((this.devices!= null)&&this.devices.equals(rhs.devices))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

    public enum Depth {

        REGISTRIES("registries"),
        ENTRIES("entries"),
        DETAILS("details");
        private final String value;
        private final static Map<String, Depths.Depth> CONSTANTS = new HashMap<String, Depths.Depth>();

        static {
            for (Depths.Depth c: values()) {
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
        public static Depths.Depth fromValue(String value) {
            Depths.Depth constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
