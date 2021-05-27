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
 * Device Config Schema
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
    "localnet",
    "pointset"
})
@Generated("jsonschema2pojo")
public class Config {

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
    private Config.Version version;
    /**
     * System Config snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("system")
    private FileConfigSystemJson system;
    /**
     * Gateway Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    private FileConfigGatewayJson gateway;
    /**
     * Proxy Device Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    private FileConfigLocalnetJson localnet;
    /**
     * pointset config snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    private FileConfigPointsetJson pointset;

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
    public Config.Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(Config.Version version) {
        this.version = version;
    }

    /**
     * System Config snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("system")
    public FileConfigSystemJson getSystem() {
        return system;
    }

    /**
     * System Config snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("system")
    public void setSystem(FileConfigSystemJson system) {
        this.system = system;
    }

    /**
     * Gateway Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public FileConfigGatewayJson getGateway() {
        return gateway;
    }

    /**
     * Gateway Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public void setGateway(FileConfigGatewayJson gateway) {
        this.gateway = gateway;
    }

    /**
     * Proxy Device Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    public FileConfigLocalnetJson getLocalnet() {
        return localnet;
    }

    /**
     * Proxy Device Config Snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    public void setLocalnet(FileConfigLocalnetJson localnet) {
        this.localnet = localnet;
    }

    /**
     * pointset config snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public FileConfigPointsetJson getPointset() {
        return pointset;
    }

    /**
     * pointset config snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public void setPointset(FileConfigPointsetJson pointset) {
        this.pointset = pointset;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Config.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
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
        sb.append("localnet");
        sb.append('=');
        sb.append(((this.localnet == null)?"<null>":this.localnet));
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
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.pointset == null)? 0 :this.pointset.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.gateway == null)? 0 :this.gateway.hashCode()));
        result = ((result* 31)+((this.localnet == null)? 0 :this.localnet.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Config) == false) {
            return false;
        }
        Config rhs = ((Config) other);
        return (((((((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system)))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, Config.Version> CONSTANTS = new HashMap<String, Config.Version>();

        static {
            for (Config.Version c: values()) {
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
        public static Config.Version fromValue(String value) {
            Config.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
