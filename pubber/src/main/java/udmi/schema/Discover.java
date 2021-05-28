
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Discover
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "protocol",
    "local_id",
    "points"
})
@Generated("jsonschema2pojo")
public class Discover {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    public Date timestamp;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public Discover.Version version;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("protocol")
    public String protocol;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("local_id")
    public String local_id;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public Object points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.protocol == null)? 0 :this.protocol.hashCode()));
        result = ((result* 31)+((this.local_id == null)? 0 :this.local_id.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Discover) == false) {
            return false;
        }
        Discover rhs = ((Discover) other);
        return ((((((this.protocol == rhs.protocol)||((this.protocol!= null)&&this.protocol.equals(rhs.protocol)))&&((this.local_id == rhs.local_id)||((this.local_id!= null)&&this.local_id.equals(rhs.local_id))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, Discover.Version> CONSTANTS = new HashMap<String, Discover.Version>();

        static {
            for (Discover.Version c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Version(String value) {
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
        public static Discover.Version fromValue(String value) {
            Discover.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
