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
 * Device State schema
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "system",
    "gateway",
    "pointset"
})
@Generated("jsonschema2pojo")
public class State {

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
    private State.Version version;
    /**
     * System state snippet
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    private FileStateSystemJson system;
    /**
     * Gateway Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    private FileStateGatewayJson gateway;
    /**
     * pointset state snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    private FileStatePointsetJson pointset;

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
    public State.Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(State.Version version) {
        this.version = version;
    }

    /**
     * System state snippet
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public FileStateSystemJson getSystem() {
        return system;
    }

    /**
     * System state snippet
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public void setSystem(FileStateSystemJson system) {
        this.system = system;
    }

    /**
     * Gateway Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public FileStateGatewayJson getGateway() {
        return gateway;
    }

    /**
     * Gateway Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public void setGateway(FileStateGatewayJson gateway) {
        this.gateway = gateway;
    }

    /**
     * pointset state snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public FileStatePointsetJson getPointset() {
        return pointset;
    }

    /**
     * pointset state snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public void setPointset(FileStatePointsetJson pointset) {
        this.pointset = pointset;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(State.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("timestamp");
        sb.append('=');
        sb.append(((this.timestamp == null)?"<null>":this.timestamp));
        sb.append(',');
        sb.append("version");
        sb.append('=');
        sb.append(((this.version == null)?"<null>":this.version));
        sb.append(',');
        sb.append("system");
        sb.append('=');
        sb.append(((this.system == null)?"<null>":this.system));
        sb.append(',');
        sb.append("gateway");
        sb.append('=');
        sb.append(((this.gateway == null)?"<null>":this.gateway));
        sb.append(',');
        sb.append("pointset");
        sb.append('=');
        sb.append(((this.pointset == null)?"<null>":this.pointset));
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
        result = ((result* 31)+((this.pointset == null)? 0 :this.pointset.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.gateway == null)? 0 :this.gateway.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof State) == false) {
            return false;
        }
        State rhs = ((State) other);
        return ((((((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset)))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, State.Version> CONSTANTS = new HashMap<String, State.Version>();

        static {
            for (State.Version c: values()) {
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
        public static State.Version fromValue(String value) {
            State.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
