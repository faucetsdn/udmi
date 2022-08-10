
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Endpoint Configuration
 * <p>
 * Parameters to define an MQTT endpoint
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "bridgeHostname",
    "bridgePort",
    "cloudRegion",
    "projectId",
    "registryId"
})
@Generated("jsonschema2pojo")
public class EndpointConfiguration {

    @JsonProperty("bridgeHostname")
    public String bridgeHostname = "mqtt.googleapis.com";
    @JsonProperty("bridgePort")
    public String bridgePort = "443";
    @JsonProperty("cloudRegion")
    public String cloudRegion;
    @JsonProperty("projectId")
    public String projectId;
    @JsonProperty("registryId")
    public String registryId;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.registryId == null)? 0 :this.registryId.hashCode()));
        result = ((result* 31)+((this.bridgePort == null)? 0 :this.bridgePort.hashCode()));
        result = ((result* 31)+((this.projectId == null)? 0 :this.projectId.hashCode()));
        result = ((result* 31)+((this.bridgeHostname == null)? 0 :this.bridgeHostname.hashCode()));
        result = ((result* 31)+((this.cloudRegion == null)? 0 :this.cloudRegion.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof EndpointConfiguration) == false) {
            return false;
        }
        EndpointConfiguration rhs = ((EndpointConfiguration) other);
        return ((((((this.registryId == rhs.registryId)||((this.registryId!= null)&&this.registryId.equals(rhs.registryId)))&&((this.bridgePort == rhs.bridgePort)||((this.bridgePort!= null)&&this.bridgePort.equals(rhs.bridgePort))))&&((this.projectId == rhs.projectId)||((this.projectId!= null)&&this.projectId.equals(rhs.projectId))))&&((this.bridgeHostname == rhs.bridgeHostname)||((this.bridgeHostname!= null)&&this.bridgeHostname.equals(rhs.bridgeHostname))))&&((this.cloudRegion == rhs.cloudRegion)||((this.cloudRegion!= null)&&this.cloudRegion.equals(rhs.cloudRegion))));
    }

}
