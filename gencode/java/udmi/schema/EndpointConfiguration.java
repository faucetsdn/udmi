
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
    "hostname",
    "port",
    "client_id"
})
@Generated("jsonschema2pojo")
public class EndpointConfiguration {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("hostname")
    public String hostname;
    @JsonProperty("port")
    public String port = "8883";
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("client_id")
    public String client_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.hostname == null)? 0 :this.hostname.hashCode()));
        result = ((result* 31)+((this.port == null)? 0 :this.port.hashCode()));
        result = ((result* 31)+((this.client_id == null)? 0 :this.client_id.hashCode()));
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
        return ((((this.hostname == rhs.hostname)||((this.hostname!= null)&&this.hostname.equals(rhs.hostname)))&&((this.port == rhs.port)||((this.port!= null)&&this.port.equals(rhs.port))))&&((this.client_id == rhs.client_id)||((this.client_id!= null)&&this.client_id.equals(rhs.client_id))));
    }

}
