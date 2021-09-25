
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Config
 * <p>
 * The config block controls a device's intended behavior. Read more: <https://github.com/faucetsdn/udmi/blob/master/docs/config.md>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "system",
    "gateway",
    "localnet",
    "blobset",
    "pointset"
})
@Generated("jsonschema2pojo")
public class Config {

    /**
     * RFC 3339 timestamp the configuration was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the configuration was generated")
    public Date timestamp;
    /**
     * Major version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Major version of the UDMI schema")
    public Integer version;
    /**
     * System Config
     * <p>
     * 
     * 
     */
    @JsonProperty("system")
    public SystemConfig system;
    /**
     * Gateway Config
     * <p>
     * 
     * 
     */
    @JsonProperty("gateway")
    public GatewayConfig gateway;
    /**
     * Localnet Config
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    public LocalnetConfig localnet;
    /**
     * Blobset Config
     * <p>
     * 
     * 
     */
    @JsonProperty("blobset")
    public BlobsetConfig blobset;
    /**
     * Pointset Config
     * <p>
     * 
     * 
     */
    @JsonProperty("pointset")
    public PointsetConfig pointset;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.pointset == null)? 0 :this.pointset.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.blobset == null)? 0 :this.blobset.hashCode()));
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
        return ((((((((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system)))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.blobset == rhs.blobset)||((this.blobset!= null)&&this.blobset.equals(rhs.blobset))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
