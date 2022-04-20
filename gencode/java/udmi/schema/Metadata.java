
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Metadata
 * <p>
 * [Metadata](../docs/specs/metadata.md) is a description about the device: a specification about how the device should be configured and expectations about what the device should be doing. Defined by `metadata.json`
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "description",
    "hash",
    "cloud",
    "system",
    "gateway",
    "discovery",
    "localnet",
    "testing",
    "pointset"
})
@Generated("jsonschema2pojo")
public class Metadata {

    /**
     * RFC 3339 timestamp the message was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the message was generated")
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
     * Generic human-readable text describing the device
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Generic human-readable text describing the device")
    public String description;
    /**
     * Automatically generated field that contains the hash of file contents.
     * 
     */
    @JsonProperty("hash")
    @JsonPropertyDescription("Automatically generated field that contains the hash of file contents.")
    public String hash;
    /**
     * Cloud Model
     * <p>
     * Information specific to how the device communicates with the cloud.
     * 
     */
    @JsonProperty("cloud")
    @JsonPropertyDescription("Information specific to how the device communicates with the cloud.")
    public CloudModel cloud;
    /**
     * System Model
     * <p>
     * High-level system information about the device. [System Model Documentation](../docs/messages/system.md)
     * (Required)
     * 
     */
    @JsonProperty("system")
    @JsonPropertyDescription("High-level system information about the device. [System Model Documentation](../docs/messages/system.md)")
    public SystemModel system;
    /**
     * Gateway Model
     * <p>
     * [Gateway Documentation](../docs/specs/gateway.md)
     * 
     */
    @JsonProperty("gateway")
    @JsonPropertyDescription("[Gateway Documentation](../docs/specs/gateway.md)")
    public GatewayModel gateway;
    /**
     * Discovery Model
     * <p>
     * Discovery target parameters
     * 
     */
    @JsonProperty("discovery")
    @JsonPropertyDescription("Discovery target parameters")
    public DiscoveryModel discovery;
    /**
     * Localnet Model
     * <p>
     * Used to describe device local network parameters
     * 
     */
    @JsonProperty("localnet")
    @JsonPropertyDescription("Used to describe device local network parameters")
    public LocalnetModel localnet;
    /**
     * Testing Model
     * <p>
     * Testing target parameters
     * 
     */
    @JsonProperty("testing")
    @JsonPropertyDescription("Testing target parameters")
    public TestingModel testing;
    /**
     * Pointset Model
     * <p>
     * Pointset representing the abstract system expectation for what the device should be doing, and how it should be configured and operated. This block specifies the expected points that a device holds
     * 
     */
    @JsonProperty("pointset")
    @JsonPropertyDescription("Pointset representing the abstract system expectation for what the device should be doing, and how it should be configured and operated. This block specifies the expected points that a device holds")
    public PointsetModel pointset;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.cloud == null)? 0 :this.cloud.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
        result = ((result* 31)+((this.testing == null)? 0 :this.testing.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
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
        return ((((((((((((this.cloud == rhs.cloud)||((this.cloud!= null)&&this.cloud.equals(rhs.cloud)))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))))&&((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery))))&&((this.testing == rhs.testing)||((this.testing!= null)&&this.testing.equals(rhs.testing))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.hash == rhs.hash)||((this.hash!= null)&&this.hash.equals(rhs.hash))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
