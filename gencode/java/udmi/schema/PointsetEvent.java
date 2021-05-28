
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
 * Pointset Event
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
public class PointsetEvent {

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
    public PointsetEvent.Version version;
    @JsonProperty("config_etag")
    public java.lang.String config_etag;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public HashMap<String, PointPointsetEvent> points;

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
        if ((other instanceof PointsetEvent) == false) {
            return false;
        }
        PointsetEvent rhs = ((PointsetEvent) other);
        return (((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.config_etag == rhs.config_etag)||((this.config_etag!= null)&&this.config_etag.equals(rhs.config_etag))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final java.lang.String value;
        private final static Map<java.lang.String, PointsetEvent.Version> CONSTANTS = new HashMap<java.lang.String, PointsetEvent.Version>();

        static {
            for (PointsetEvent.Version c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Version(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static PointsetEvent.Version fromValue(java.lang.String value) {
            PointsetEvent.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
