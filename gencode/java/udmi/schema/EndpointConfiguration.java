
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
    "name",
    "protocol",
    "transport",
    "hostname",
    "payload",
    "error",
    "port",
    "config_sync_sec",
    "client_id",
    "topic_prefix",
    "recv_id",
    "send_id",
    "side_id",
    "gatewayId",
    "deviceId",
    "enabled",
    "noConfigAck",
    "capacity",
    "publish_delay_sec",
    "periodic_sec",
    "keyBytes",
    "algorithm",
    "auth_provider",
    "generation"
})
public class EndpointConfiguration {

    /**
     * Friendly name for this flow (debugging and diagnostics)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Friendly name for this flow (debugging and diagnostics)")
    public String name;
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
     * Simple payload template for simple injection use cases
     * 
     */
    @JsonProperty("payload")
    @JsonPropertyDescription("Simple payload template for simple injection use cases")
    public String payload;
    /**
     * Error message container for capturing errors during parsing/handling
     * 
     */
    @JsonProperty("error")
    @JsonPropertyDescription("Error message container for capturing errors during parsing/handling")
    public String error;
    @JsonProperty("port")
    public Integer port;
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
     * Prefix for message topics
     * 
     */
    @JsonProperty("topic_prefix")
    @JsonPropertyDescription("Prefix for message topics")
    public String topic_prefix;
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
     * Id for a side-car message channel
     * 
     */
    @JsonProperty("side_id")
    @JsonPropertyDescription("Id for a side-car message channel")
    public String side_id;
    @JsonProperty("gatewayId")
    public String gatewayId;
    @JsonProperty("deviceId")
    public String deviceId;
    /**
     * Indicator if this endpoint should be active (null or non-empty)
     * 
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("Indicator if this endpoint should be active (null or non-empty)")
    public String enabled;
    /**
     * True if config messages should not be acked (lower QOS)
     * 
     */
    @JsonProperty("noConfigAck")
    @JsonPropertyDescription("True if config messages should not be acked (lower QOS)")
    public Boolean noConfigAck;
    /**
     * Queue capacity for limiting pipes
     * 
     */
    @JsonProperty("capacity")
    @JsonPropertyDescription("Queue capacity for limiting pipes")
    public Integer capacity;
    /**
     * Artifical publish delay for testing
     * 
     */
    @JsonProperty("publish_delay_sec")
    @JsonPropertyDescription("Artifical publish delay for testing")
    public Integer publish_delay_sec;
    /**
     * Rate for periodic task execution
     * 
     */
    @JsonProperty("periodic_sec")
    @JsonPropertyDescription("Rate for periodic task execution")
    public Integer periodic_sec;
    @JsonProperty("keyBytes")
    public Object keyBytes;
    @JsonProperty("algorithm")
    public String algorithm;
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
        result = ((result* 31)+((this.keyBytes == null)? 0 :this.keyBytes.hashCode()));
        result = ((result* 31)+((this.side_id == null)? 0 :this.side_id.hashCode()));
        result = ((result* 31)+((this.transport == null)? 0 :this.transport.hashCode()));
        result = ((result* 31)+((this.publish_delay_sec == null)? 0 :this.publish_delay_sec.hashCode()));
        result = ((result* 31)+((this.error == null)? 0 :this.error.hashCode()));
        result = ((result* 31)+((this.config_sync_sec == null)? 0 :this.config_sync_sec.hashCode()));
        result = ((result* 31)+((this.deviceId == null)? 0 :this.deviceId.hashCode()));
        result = ((result* 31)+((this.client_id == null)? 0 :this.client_id.hashCode()));
        result = ((result* 31)+((this.enabled == null)? 0 :this.enabled.hashCode()));
        result = ((result* 31)+((this.capacity == null)? 0 :this.capacity.hashCode()));
        result = ((result* 31)+((this.send_id == null)? 0 :this.send_id.hashCode()));
        result = ((result* 31)+((this.protocol == null)? 0 :this.protocol.hashCode()));
        result = ((result* 31)+((this.hostname == null)? 0 :this.hostname.hashCode()));
        result = ((result* 31)+((this.payload == null)? 0 :this.payload.hashCode()));
        result = ((result* 31)+((this.port == null)? 0 :this.port.hashCode()));
        result = ((result* 31)+((this.topic_prefix == null)? 0 :this.topic_prefix.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.periodic_sec == null)? 0 :this.periodic_sec.hashCode()));
        result = ((result* 31)+((this.noConfigAck == null)? 0 :this.noConfigAck.hashCode()));
        result = ((result* 31)+((this.recv_id == null)? 0 :this.recv_id.hashCode()));
        result = ((result* 31)+((this.gatewayId == null)? 0 :this.gatewayId.hashCode()));
        result = ((result* 31)+((this.auth_provider == null)? 0 :this.auth_provider.hashCode()));
        result = ((result* 31)+((this.algorithm == null)? 0 :this.algorithm.hashCode()));
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
        return (((((((((((((((((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.keyBytes == rhs.keyBytes)||((this.keyBytes!= null)&&this.keyBytes.equals(rhs.keyBytes))))&&((this.side_id == rhs.side_id)||((this.side_id!= null)&&this.side_id.equals(rhs.side_id))))&&((this.transport == rhs.transport)||((this.transport!= null)&&this.transport.equals(rhs.transport))))&&((this.publish_delay_sec == rhs.publish_delay_sec)||((this.publish_delay_sec!= null)&&this.publish_delay_sec.equals(rhs.publish_delay_sec))))&&((this.error == rhs.error)||((this.error!= null)&&this.error.equals(rhs.error))))&&((this.config_sync_sec == rhs.config_sync_sec)||((this.config_sync_sec!= null)&&this.config_sync_sec.equals(rhs.config_sync_sec))))&&((this.deviceId == rhs.deviceId)||((this.deviceId!= null)&&this.deviceId.equals(rhs.deviceId))))&&((this.client_id == rhs.client_id)||((this.client_id!= null)&&this.client_id.equals(rhs.client_id))))&&((this.enabled == rhs.enabled)||((this.enabled!= null)&&this.enabled.equals(rhs.enabled))))&&((this.capacity == rhs.capacity)||((this.capacity!= null)&&this.capacity.equals(rhs.capacity))))&&((this.send_id == rhs.send_id)||((this.send_id!= null)&&this.send_id.equals(rhs.send_id))))&&((this.protocol == rhs.protocol)||((this.protocol!= null)&&this.protocol.equals(rhs.protocol))))&&((this.hostname == rhs.hostname)||((this.hostname!= null)&&this.hostname.equals(rhs.hostname))))&&((this.payload == rhs.payload)||((this.payload!= null)&&this.payload.equals(rhs.payload))))&&((this.port == rhs.port)||((this.port!= null)&&this.port.equals(rhs.port))))&&((this.topic_prefix == rhs.topic_prefix)||((this.topic_prefix!= null)&&this.topic_prefix.equals(rhs.topic_prefix))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.periodic_sec == rhs.periodic_sec)||((this.periodic_sec!= null)&&this.periodic_sec.equals(rhs.periodic_sec))))&&((this.noConfigAck == rhs.noConfigAck)||((this.noConfigAck!= null)&&this.noConfigAck.equals(rhs.noConfigAck))))&&((this.recv_id == rhs.recv_id)||((this.recv_id!= null)&&this.recv_id.equals(rhs.recv_id))))&&((this.gatewayId == rhs.gatewayId)||((this.gatewayId!= null)&&this.gatewayId.equals(rhs.gatewayId))))&&((this.auth_provider == rhs.auth_provider)||((this.auth_provider!= null)&&this.auth_provider.equals(rhs.auth_provider))))&&((this.algorithm == rhs.algorithm)||((this.algorithm!= null)&&this.algorithm.equals(rhs.algorithm))));
    }

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
