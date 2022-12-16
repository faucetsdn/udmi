
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
 * Parameters to define an MQTT endpoint
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "protocol",
    "transport",
    "hostname",
    "port",
    "config_sync_sec",
    "client_id",
    "topic_prefix",
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
    @JsonProperty("port")
    public Integer port = 8883;
    /**
     * Delay waiting for config message on start, <0 to disable
     * 
     */
    @JsonProperty("config_sync_sec")
    @JsonPropertyDescription("Delay waiting for config message on start, <0 to disable")
    public Integer config_sync_sec;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("client_id")
    public String client_id;
    /**
     * Prefix for message topics
     * 
     */
    @JsonProperty("topic_prefix")
    @JsonPropertyDescription("Prefix for message topics")
    public String topic_prefix;
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
        result = ((result* 31)+((this.protocol == null)? 0 :this.protocol.hashCode()));
        result = ((result* 31)+((this.hostname == null)? 0 :this.hostname.hashCode()));
        result = ((result* 31)+((this.port == null)? 0 :this.port.hashCode()));
        result = ((result* 31)+((this.topic_prefix == null)? 0 :this.topic_prefix.hashCode()));
        result = ((result* 31)+((this.transport == null)? 0 :this.transport.hashCode()));
        result = ((result* 31)+((this.config_sync_sec == null)? 0 :this.config_sync_sec.hashCode()));
        result = ((result* 31)+((this.client_id == null)? 0 :this.client_id.hashCode()));
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
        return ((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.protocol == rhs.protocol)||((this.protocol!= null)&&this.protocol.equals(rhs.protocol))))&&((this.hostname == rhs.hostname)||((this.hostname!= null)&&this.hostname.equals(rhs.hostname))))&&((this.port == rhs.port)||((this.port!= null)&&this.port.equals(rhs.port))))&&((this.topic_prefix == rhs.topic_prefix)||((this.topic_prefix!= null)&&this.topic_prefix.equals(rhs.topic_prefix))))&&((this.transport == rhs.transport)||((this.transport!= null)&&this.transport.equals(rhs.transport))))&&((this.config_sync_sec == rhs.config_sync_sec)||((this.config_sync_sec!= null)&&this.config_sync_sec.equals(rhs.config_sync_sec))))&&((this.client_id == rhs.client_id)||((this.client_id!= null)&&this.client_id.equals(rhs.client_id))))&&((this.auth_provider == rhs.auth_provider)||((this.auth_provider!= null)&&this.auth_provider.equals(rhs.auth_provider))));
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
