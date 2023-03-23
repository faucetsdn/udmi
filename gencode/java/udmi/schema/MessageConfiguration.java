
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
 * Message Configuration
 * <p>
 * Parameters for configuring a message in/out pipeline
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "transport",
    "namespace",
    "source",
    "destination"
})
@Generated("jsonschema2pojo")
public class MessageConfiguration {

    @JsonProperty("transport")
    public MessageConfiguration.Transport transport;
    @JsonProperty("namespace")
    public String namespace;
    @JsonProperty("source")
    public String source;
    @JsonProperty("destination")
    public String destination;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.namespace == null)? 0 :this.namespace.hashCode()));
        result = ((result* 31)+((this.destination == null)? 0 :this.destination.hashCode()));
        result = ((result* 31)+((this.transport == null)? 0 :this.transport.hashCode()));
        result = ((result* 31)+((this.source == null)? 0 :this.source.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MessageConfiguration) == false) {
            return false;
        }
        MessageConfiguration rhs = ((MessageConfiguration) other);
        return (((((this.namespace == rhs.namespace)||((this.namespace!= null)&&this.namespace.equals(rhs.namespace)))&&((this.destination == rhs.destination)||((this.destination!= null)&&this.destination.equals(rhs.destination))))&&((this.transport == rhs.transport)||((this.transport!= null)&&this.transport.equals(rhs.transport))))&&((this.source == rhs.source)||((this.source!= null)&&this.source.equals(rhs.source))));
    }

    @Generated("jsonschema2pojo")
    public enum Transport {

        LOCAL("local"),
        PUBSUB("pubsub");
        private final String value;
        private final static Map<String, MessageConfiguration.Transport> CONSTANTS = new HashMap<String, MessageConfiguration.Transport>();

        static {
            for (MessageConfiguration.Transport c: values()) {
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
        public static MessageConfiguration.Transport fromValue(String value) {
            MessageConfiguration.Transport constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
