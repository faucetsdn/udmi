
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
 * The config block controls a device's intended behavior. [Config Documentation](../docs/messages/config.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "system",
    "gateway",
    "discovery",
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
    public String version;
    /**
     * System Config
     * <p>
     * [System Config Documentation](../docs/messages/system.md#config)
     * 
     */
    @JsonProperty("system")
    @JsonPropertyDescription("[System Config Documentation](../docs/messages/system.md#config)")
    public SystemConfig system;
    /**
     * Gateway Config
     * <p>
     * Configuration for gateways. Only required for devices which are acting as [gateways](../docs/specs/gateway.md)
     * 
     */
    @JsonProperty("gateway")
    @JsonPropertyDescription("Configuration for gateways. Only required for devices which are acting as [gateways](../docs/specs/gateway.md)")
    public GatewayConfig gateway;
    /**
     * Discovery Config
     * <p>
     * Configuration for [discovery](../docs/specs/discovery.md)
     * 
     */
    @JsonProperty("discovery")
    @JsonPropertyDescription("Configuration for [discovery](../docs/specs/discovery.md)")
    public DiscoveryConfig discovery;
    /**
     * Localnet Config
     * <p>
     * Used to describe device local network parameters
     * 
     */
    @JsonProperty("localnet")
    @JsonPropertyDescription("Used to describe device local network parameters")
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
     * [Pointset Config Documentation](../docs/messages/pointset.md#config)
     * 
     */
    @JsonProperty("pointset")
    @JsonPropertyDescription("[Pointset Config Documentation](../docs/messages/pointset.md#config)")
    public PointsetConfig pointset;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
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
        return (((((((((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system)))&&((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery))))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.blobset == rhs.blobset)||((this.blobset!= null)&&this.blobset.equals(rhs.blobset))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
