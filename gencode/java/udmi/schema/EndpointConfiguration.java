
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Endpoint Configuration
 * <p>
 * Parameters to define a message endpoint
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "protocol",
    "transport",
    "hostname",
    "error",
    "port",
    "config_sync_sec",
    "client_id",
    "msg_prefix",
    "recv_id",
    "send_id",
    "distributor",
    "auth_provider",
    "generation"
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
    @JsonProperty("transport")
    public EndpointConfiguration.Transport transport;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("hostname")
    public String hostname;
    /**
     * Error message container for capturing errors during parsing/handling
     * 
     */
    @JsonProperty("error")
    @JsonPropertyDescription("Error message container for capturing errors during parsing/handling")
    public String error;
    @JsonProperty("port")
    public Integer port = 8883;
    /**
     * Delay waiting for config message on start, 0 for default, <0 to disable
     * 
     */
    @JsonProperty("config_sync_sec")
    @JsonPropertyDescription("Delay waiting for config message on start, 0 for default, <0 to disable")
    public Integer config_sync_sec;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("client_id")
    public String client_id;
    /**
     * Prefix for message topics/addresses
     * 
     */
    @JsonProperty("msg_prefix")
    @JsonPropertyDescription("Prefix for message topics/addresses")
    public String msg_prefix;
    /**
     * Id for the receiving message channel
     * 
     */
    @JsonProperty("recv_id")
    @JsonPropertyDescription("Id for the receiving message channel")
    public String recv_id;
    /**
     * Id for the sending messages channel
     * 
     */
    @JsonProperty("send_id")
    @JsonPropertyDescription("Id for the sending messages channel")
    public String send_id;
    /**
     * processor designation for a distributor channel
     * 
     */
    @JsonProperty("distributor")
    @JsonPropertyDescription("processor designation for a distributor channel")
    public String distributor;
    @JsonProperty("auth_provider")
    public Auth_provider auth_provider;
    /**
     * The timestamp of the endpoint generation
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("The timestamp of the endpoint generation")
    public Date generation;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.transport == null)? 0 :this.transport.hashCode()));
        result = ((result* 31)+((this.error == null)? 0 :this.error.hashCode()));
        result = ((result* 31)+((this.config_sync_sec == null)? 0 :this.config_sync_sec.hashCode()));
        result = ((result* 31)+((this.distributor == null)? 0 :this.distributor.hashCode()));
        result = ((result* 31)+((this.client_id == null)? 0 :this.client_id.hashCode()));
        result = ((result* 31)+((this.msg_prefix == null)? 0 :this.msg_prefix.hashCode()));
        result = ((result* 31)+((this.send_id == null)? 0 :this.send_id.hashCode()));
        result = ((result* 31)+((this.protocol == null)? 0 :this.protocol.hashCode()));
        result = ((result* 31)+((this.hostname == null)? 0 :this.hostname.hashCode()));
        result = ((result* 31)+((this.port == null)? 0 :this.port.hashCode()));
        result = ((result* 31)+((this.recv_id == null)? 0 :this.recv_id.hashCode()));
        result = ((result* 31)+((this.auth_provider == null)? 0 :this.auth_provider.hashCode()));
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
        return ((((((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.transport == rhs.transport)||((this.transport!= null)&&this.transport.equals(rhs.transport))))&&((this.error == rhs.error)||((this.error!= null)&&this.error.equals(rhs.error))))&&((this.config_sync_sec == rhs.config_sync_sec)||((this.config_sync_sec!= null)&&this.config_sync_sec.equals(rhs.config_sync_sec))))&&((this.distributor == rhs.distributor)||((this.distributor!= null)&&this.distributor.equals(rhs.distributor))))&&((this.client_id == rhs.client_id)||((this.client_id!= null)&&this.client_id.equals(rhs.client_id))))&&((this.msg_prefix == rhs.msg_prefix)||((this.msg_prefix!= null)&&this.msg_prefix.equals(rhs.msg_prefix))))&&((this.send_id == rhs.send_id)||((this.send_id!= null)&&this.send_id.equals(rhs.send_id))))&&((this.protocol == rhs.protocol)||((this.protocol!= null)&&this.protocol.equals(rhs.protocol))))&&((this.hostname == rhs.hostname)||((this.hostname!= null)&&this.hostname.equals(rhs.hostname))))&&((this.port == rhs.port)||((this.port!= null)&&this.port.equals(rhs.port))))&&((this.recv_id == rhs.recv_id)||((this.recv_id!= null)&&this.recv_id.equals(rhs.recv_id))))&&((this.auth_provider == rhs.auth_provider)||((this.auth_provider!= null)&&this.auth_provider.equals(rhs.auth_provider))));
    }

    @Generated("jsonschema2pojo")
    public enum Protocol {

        LOCAL("local"),
        PUBSUB("pubsub"),
        FILE("file"),
        TRACE("trace"),
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

    @Generated("jsonschema2pojo")
    public enum Transport {

        SSL("ssl"),
        TCP("tcp");
        private final String value;
        private final static Map<String, EndpointConfiguration.Transport> CONSTANTS = new HashMap<String, EndpointConfiguration.Transport>();

        static {
            for (EndpointConfiguration.Transport c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Transport(String value) {
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
        public static EndpointConfiguration.Transport fromValue(String value) {
            EndpointConfiguration.Transport constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
