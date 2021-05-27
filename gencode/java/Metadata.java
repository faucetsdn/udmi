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
 * Device metadata schema
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "hash",
    "cloud",
    "system",
    "gateway",
    "localnet",
    "pointset"
})
@Generated("jsonschema2pojo")
public class Metadata {

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
    private Metadata.Version version;
    @JsonProperty("hash")
    private String hash;
    /**
     * Cloud configuration metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("cloud")
    private FileMetadataCloudJson cloud;
    /**
     * System metadata snippet
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    private FileMetadataSystemJson system;
    /**
     * Gateway metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    private FileMetadataGatewayJson gateway;
    /**
     * Local network metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    private FileMetadataLocalnetJson localnet;
    /**
     * Pointset metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    private FileMetadataPointsetJson pointset;

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
    public Metadata.Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(Metadata.Version version) {
        this.version = version;
    }

    @JsonProperty("hash")
    public String getHash() {
        return hash;
    }

    @JsonProperty("hash")
    public void setHash(String hash) {
        this.hash = hash;
    }

    /**
     * Cloud configuration metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("cloud")
    public FileMetadataCloudJson getCloud() {
        return cloud;
    }

    /**
     * Cloud configuration metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("cloud")
    public void setCloud(FileMetadataCloudJson cloud) {
        this.cloud = cloud;
    }

    /**
     * System metadata snippet
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public FileMetadataSystemJson getSystem() {
        return system;
    }

    /**
     * System metadata snippet
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public void setSystem(FileMetadataSystemJson system) {
        this.system = system;
    }

    /**
     * Gateway metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public FileMetadataGatewayJson getGateway() {
        return gateway;
    }

    /**
     * Gateway metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public void setGateway(FileMetadataGatewayJson gateway) {
        this.gateway = gateway;
    }

    /**
     * Local network metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    public FileMetadataLocalnetJson getLocalnet() {
        return localnet;
    }

    /**
     * Local network metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    public void setLocalnet(FileMetadataLocalnetJson localnet) {
        this.localnet = localnet;
    }

    /**
     * Pointset metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public FileMetadataPointsetJson getPointset() {
        return pointset;
    }

    /**
     * Pointset metadata snippet
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public void setPointset(FileMetadataPointsetJson pointset) {
        this.pointset = pointset;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Metadata.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("timestamp");
        sb.append('=');
        sb.append(((this.timestamp == null)?"<null>":this.timestamp));
        sb.append(',');
        sb.append("version");
        sb.append('=');
        sb.append(((this.version == null)?"<null>":this.version));
        sb.append(',');
        sb.append("hash");
        sb.append('=');
        sb.append(((this.hash == null)?"<null>":this.hash));
        sb.append(',');
        sb.append("cloud");
        sb.append('=');
        sb.append(((this.cloud == null)?"<null>":this.cloud));
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
        result = ((result* 31)+((this.cloud == null)? 0 :this.cloud.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.pointset == null)? 0 :this.pointset.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.hash == null)? 0 :this.hash.hashCode()));
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
        if ((other instanceof Metadata) == false) {
            return false;
        }
        Metadata rhs = ((Metadata) other);
        return (((((((((this.cloud == rhs.cloud)||((this.cloud!= null)&&this.cloud.equals(rhs.cloud)))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.hash == rhs.hash)||((this.hash!= null)&&this.hash.equals(rhs.hash))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

    @Generated("jsonschema2pojo")
    public enum Version {

        _1("1");
        private final String value;
        private final static Map<String, Metadata.Version> CONSTANTS = new HashMap<String, Metadata.Version>();

        static {
            for (Metadata.Version c: values()) {
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
        public static Metadata.Version fromValue(String value) {
            Metadata.Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
