
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Endpoint Configuration
 * <p>
 * Parameters to define an MQTT endpoint
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "protocol",
    "hostname",
    "port",
    "client_id",
    "nonce"
})
@Generated("jsonschema2pojo")
public class EndpointConfiguration {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("protocol")
    public EndpointConfiguration.Protocol protocol;
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
    @JsonProperty("nonce")
    public String nonce;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.protocol == null)? 0 :this.protocol.hashCode()));
        result = ((result* 31)+((this.hostname == null)? 0 :this.hostname.hashCode()));
        result = ((result* 31)+((this.port == null)? 0 :this.port.hashCode()));
        result = ((result* 31)+((this.nonce == null)? 0 :this.nonce.hashCode()));
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
        return ((((((this.protocol == rhs.protocol)||((this.protocol!= null)&&this.protocol.equals(rhs.protocol)))&&((this.hostname == rhs.hostname)||((this.hostname!= null)&&this.hostname.equals(rhs.hostname))))&&((this.port == rhs.port)||((this.port!= null)&&this.port.equals(rhs.port))))&&((this.nonce == rhs.nonce)||((this.nonce!= null)&&this.nonce.equals(rhs.nonce))))&&((this.client_id == rhs.client_id)||((this.client_id!= null)&&this.client_id.equals(rhs.client_id))));
    }

    @Generated("jsonschema2pojo")
    public enum Protocol {

        MQTT("mqtt");
        private final String value;
        private final static Map<String, EndpointConfiguration.Protocol> CONSTANTS = new HashMap<String, EndpointConfiguration.Protocol>();

        static {
            for (EndpointConfiguration.Protocol c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Protocol(String value) {
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
        public static EndpointConfiguration.Protocol fromValue(String value) {
            EndpointConfiguration.Protocol constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
