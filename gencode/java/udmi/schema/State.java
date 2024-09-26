
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * State
 * <p>
 * [State](../docs/messages/state.md) message, defined by [`state.json`]
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "upgraded_from",
    "system",
    "gateway",
    "discovery",
    "localnet",
    "blobset",
    "pointset"
})
public class State {

    /**
     * RFC 3339 UTC Timestamp the state payload was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC Timestamp the state payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * Original version of schema pre-upgrade
     * 
     */
    @JsonProperty("upgraded_from")
    @JsonPropertyDescription("Original version of schema pre-upgrade")
    public String upgraded_from;
    /**
     * System State
     * <p>
     * [System State Documentation](../docs/messages/system.md#state)
     * (Required)
     * 
     */
    @JsonProperty("system")
    @JsonPropertyDescription("[System State Documentation](../docs/messages/system.md#state)")
    public SystemState system;
    /**
     * Gateway State
     * <p>
     * [Gateway Documentation](../docs/specs/gateway.md)
     * 
     */
    @JsonProperty("gateway")
    @JsonPropertyDescription("[Gateway Documentation](../docs/specs/gateway.md)")
    public GatewayState gateway;
    /**
     * Discovery State
     * <p>
     * State for [discovery](../docs/specs/discovery.md)
     * 
     */
    @JsonProperty("discovery")
    @JsonPropertyDescription("State for [discovery](../docs/specs/discovery.md)")
    public DiscoveryState discovery;
    /**
     * Localnet State
     * <p>
     * 
     * 
     */
    @JsonProperty("localnet")
    public LocalnetState localnet;
    /**
     * Blobset State
     * <p>
     * 
     * 
     */
    @JsonProperty("blobset")
    public BlobsetState blobset;
    /**
     * Pointset State
     * <p>
     * A set of points reporting telemetry data.
     * 
     */
    @JsonProperty("pointset")
    @JsonPropertyDescription("A set of points reporting telemetry data.")
    public PointsetState pointset;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.system == null)? 0 :this.system.hashCode()));
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
        result = ((result* 31)+((this.upgraded_from == null)? 0 :this.upgraded_from.hashCode()));
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
        if ((other instanceof State) == false) {
            return false;
        }
        State rhs = ((State) other);
        return ((((((((((this.system == rhs.system)||((this.system!= null)&&this.system.equals(rhs.system)))&&((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery))))&&((this.upgraded_from == rhs.upgraded_from)||((this.upgraded_from!= null)&&this.upgraded_from.equals(rhs.upgraded_from))))&&((this.pointset == rhs.pointset)||((this.pointset!= null)&&this.pointset.equals(rhs.pointset))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.blobset == rhs.blobset)||((this.blobset!= null)&&this.blobset.equals(rhs.blobset))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.localnet == rhs.localnet)||((this.localnet!= null)&&this.localnet.equals(rhs.localnet))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
