
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
    "upgraded_from",
    "hash",
    "operation",
    "cloud",
    "system",
    "gateway",
    "discovery",
    "localnet",
    "testing",
    "features",
    "pointset",
    "structure"
})
public class Metadata {

    /**
     * RFC 3339 timestamp UTC the data was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp UTC the data was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema for this file
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema for this file")
    public java.lang.String version;
    /**
     * Original version of the UDMI schema for this file
     * 
     */
    @JsonProperty("upgraded_from")
    @JsonPropertyDescription("Original version of the UDMI schema for this file")
    public java.lang.String upgraded_from;
    /**
     * DEPRECATED
     * 
     */
    @JsonProperty("hash")
    @JsonPropertyDescription("DEPRECATED")
    public java.lang.String hash;
    /**
     * Model Operation
     * <p>
     * 
     * 
     */
    @JsonProperty("operation")
    public CloudModel.ModelOperation operation;
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
     * Testing Model
     * <p>
     * Model of supported features
     * 
     */
    @JsonProperty("features")
    @JsonPropertyDescription("Model of supported features")
    public Map<String, FeatureDiscovery> features;
    /**
     * Pointset Model
     * <p>
     * Pointset representing the abstract system expectation for what the device should be doing, and how it should be configured and operated. This block specifies the expected points that a device holds
     * 
     */
    @JsonProperty("pointset")
    @JsonPropertyDescription("Pointset representing the abstract system expectation for what the device should be doing, and how it should be configured and operated. This block specifies the expected points that a device holds")
    public PointsetModel pointset;
    @JsonProperty("structure")
    public HashMap<String, DiscoveryEvents> structure;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.testing == null)? 0 :this.testing.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.structure == null)? 0 :this.structure.hashCode()));
        result = ((result* 31)+((this.cloud == null)? 0 :this.cloud.hashCode()));
        result = ((result* 31)+((this.features == null)? 0 :this.features.hashCode()));
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
        result = ((result* 31)+((this.upgraded_from == null)? 0 :this.upgraded_from.hashCode()));
        result = ((result* 31)+((this.pointset == null)? 0 :this.pointset.hashCode()));
        result = ((result* 31)+((this.operation == null)? 0 :this.operation.hashCode()));
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
        return (((((((((((((((this.testing == rhs.testing)||((this.testing!= null)&&this.testing.equals(rhs.testing)))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.structure == rhs.structure)||((this.structure!= null)&&this.structure.equals(rhs.structure))))&&((this.cloud == rhs.cloud)||((this.cloud!= null)&&this.cloud.equals(rhs.cloud))))&&((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features))))&&((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system))))&&((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery))))&&((this.upgraded_from == rhs.upgraded_from)||((this.upgraded_from!= null)&&this.upgraded_from.equals(rhs.upgraded_from))))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.operation == rhs.operation)||((this.operation!= null)&&this.operation.equals(rhs.operation))))&&((this.hash == rhs.hash)||((this.hash!= null)&&this.hash.equals(rhs.hash))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
