
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
 * Pointset telemetry schema
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "config_etag",
    "points"
})
@Generated("jsonschema2pojo")
public class Event_pointset {

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
    public Event_pointset.Version version;
    @JsonProperty("config_etag")
    public String config_etag;
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
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.config_etag == null)? 0 :this.config_etag.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Event_pointset) == false) {
            return false;
        }
        Event_pointset rhs = ((Event_pointset) other);
        return (((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.config_etag == rhs.config_etag)||((this.config_etag!= null)&&this.config_etag.equals(rhs.config_etag))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, Event_pointset.Version> CONSTANTS = new HashMap<String, Event_pointset.Version>();

        static {
            for (Event_pointset.Version c: values()) {
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
        public static Event_pointset.Version fromValue(String value) {
            Event_pointset.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
