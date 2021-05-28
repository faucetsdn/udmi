
package udmi.schema;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * System event schema
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "logentries"
})
@Generated("jsonschema2pojo")
public class Event_system {

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
    public Event_system.Version version;
    @JsonProperty("logentries")
    public List<Entry> logentries = new ArrayList<Entry>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.logentries == null)? 0 :this.logentries.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Event_system) == false) {
            return false;
        }
        Event_system rhs = ((Event_system) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.logentries == rhs.logentries)||((this.logentries!= null)&&this.logentries.equals(rhs.logentries))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, Event_system.Version> CONSTANTS = new HashMap<String, Event_system.Version>();

        static {
            for (Event_system.Version c: values()) {
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
        public static Event_system.Version fromValue(String value) {
            Event_system.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
