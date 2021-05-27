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
public class EventPointset {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    private Date timestamp;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    private EventPointset.Version version;
    @JsonProperty("config_etag")
    private String configEtag;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    private Object points;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public EventPointset.Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(EventPointset.Version version) {
        this.version = version;
    }

    @JsonProperty("config_etag")
    public String getConfigEtag() {
        return configEtag;
    }

    @JsonProperty("config_etag")
    public void setConfigEtag(String configEtag) {
        this.configEtag = configEtag;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public Object getPoints() {
        return points;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public void setPoints(Object points) {
        this.points = points;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(EventPointset.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("timestamp");
        sb.append('=');
        sb.append(((this.timestamp == null)?"<null>":this.timestamp));
        sb.append(',');
        sb.append("version");
        sb.append('=');
        sb.append(((this.version == null)?"<null>":this.version));
        sb.append(',');
        sb.append("configEtag");
        sb.append('=');
        sb.append(((this.configEtag == null)?"<null>":this.configEtag));
        sb.append(',');
        sb.append("points");
        sb.append('=');
        sb.append(((this.points == null)?"<null>":this.points));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.configEtag == null)? 0 :this.configEtag.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof EventPointset) == false) {
            return false;
        }
        EventPointset rhs = ((EventPointset) other);
        return (((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.configEtag == rhs.configEtag)||((this.configEtag!= null)&&this.configEtag.equals(rhs.configEtag))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, EventPointset.Version> CONSTANTS = new HashMap<String, EventPointset.Version>();

        static {
            for (EventPointset.Version c: values()) {
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
        public static EventPointset.Version fromValue(String value) {
            EventPointset.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
