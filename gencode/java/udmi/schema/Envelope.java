
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Envelope
 * <p>
 * The UDMI `envelope` is not a message itself, per se, but the attributes and other information that is delivered along with a message. [Message Envelope Documentation](../docs/messages/envelope.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "deviceId",
    "deviceNumId",
    "deviceRegistryId",
    "deviceRegistryLocation",
    "projectId",
    "payload",
    "source",
    "gatewayId",
    "transactionId",
    "publishTime",
    "rawFolder",
    "subFolder",
    "subType"
})
public class Envelope {

    @JsonProperty("deviceId")
    public String deviceId;
    @JsonProperty("deviceNumId")
    public String deviceNumId;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("deviceRegistryId")
    public String deviceRegistryId;
    @JsonProperty("deviceRegistryLocation")
    public String deviceRegistryLocation;
    @JsonProperty("projectId")
    public String projectId;
    @JsonProperty("payload")
    public String payload;
    @JsonProperty("source")
    public String source;
    @JsonProperty("gatewayId")
    public String gatewayId;
    @JsonProperty("transactionId")
    public String transactionId;
    @JsonProperty("publishTime")
    public Date publishTime;
    @JsonProperty("rawFolder")
    public String rawFolder;
    @JsonProperty("subFolder")
    public Envelope.SubFolder subFolder;
    @JsonProperty("subType")
    public Envelope.SubType subType;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.deviceRegistryLocation == null)? 0 :this.deviceRegistryLocation.hashCode()));
        result = ((result* 31)+((this.publishTime == null)? 0 :this.publishTime.hashCode()));
        result = ((result* 31)+((this.subFolder == null)? 0 :this.subFolder.hashCode()));
        result = ((result* 31)+((this.rawFolder == null)? 0 :this.rawFolder.hashCode()));
        result = ((result* 31)+((this.source == null)? 0 :this.source.hashCode()));
        result = ((result* 31)+((this.deviceId == null)? 0 :this.deviceId.hashCode()));
        result = ((result* 31)+((this.transactionId == null)? 0 :this.transactionId.hashCode()));
        result = ((result* 31)+((this.deviceNumId == null)? 0 :this.deviceNumId.hashCode()));
        result = ((result* 31)+((this.payload == null)? 0 :this.payload.hashCode()));
        result = ((result* 31)+((this.deviceRegistryId == null)? 0 :this.deviceRegistryId.hashCode()));
        result = ((result* 31)+((this.subType == null)? 0 :this.subType.hashCode()));
        result = ((result* 31)+((this.projectId == null)? 0 :this.projectId.hashCode()));
        result = ((result* 31)+((this.gatewayId == null)? 0 :this.gatewayId.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Envelope) == false) {
            return false;
        }
        Envelope rhs = ((Envelope) other);
        return ((((((((((((((this.deviceRegistryLocation == rhs.deviceRegistryLocation)||((this.deviceRegistryLocation!= null)&&this.deviceRegistryLocation.equals(rhs.deviceRegistryLocation)))&&((this.publishTime == rhs.publishTime)||((this.publishTime!= null)&&this.publishTime.equals(rhs.publishTime))))&&((this.subFolder == rhs.subFolder)||((this.subFolder!= null)&&this.subFolder.equals(rhs.subFolder))))&&((this.rawFolder == rhs.rawFolder)||((this.rawFolder!= null)&&this.rawFolder.equals(rhs.rawFolder))))&&((this.source == rhs.source)||((this.source!= null)&&this.source.equals(rhs.source))))&&((this.deviceId == rhs.deviceId)||((this.deviceId!= null)&&this.deviceId.equals(rhs.deviceId))))&&((this.transactionId == rhs.transactionId)||((this.transactionId!= null)&&this.transactionId.equals(rhs.transactionId))))&&((this.deviceNumId == rhs.deviceNumId)||((this.deviceNumId!= null)&&this.deviceNumId.equals(rhs.deviceNumId))))&&((this.payload == rhs.payload)||((this.payload!= null)&&this.payload.equals(rhs.payload))))&&((this.deviceRegistryId == rhs.deviceRegistryId)||((this.deviceRegistryId!= null)&&this.deviceRegistryId.equals(rhs.deviceRegistryId))))&&((this.subType == rhs.subType)||((this.subType!= null)&&this.subType.equals(rhs.subType))))&&((this.projectId == rhs.projectId)||((this.projectId!= null)&&this.projectId.equals(rhs.projectId))))&&((this.gatewayId == rhs.gatewayId)||((this.gatewayId!= null)&&this.gatewayId.equals(rhs.gatewayId))));
    }

    public enum SubFolder {

        UDMI("udmi"),
        UPDATE("update"),
        INVALID("invalid"),
        ERROR("error"),
        CLOUD("cloud"),
        DISCOVERY("discovery"),
        MAPPING("mapping"),
        SYSTEM("system"),
        GATEWAY("gateway"),
        SWARM("swarm"),
        LOCALNET("localnet"),
        POINTSET("pointset"),
        VALIDATION("validation"),
        BLOBSET("blobset"),
        MONITORING("monitoring");
        private final String value;
        private final static Map<String, Envelope.SubFolder> CONSTANTS = new HashMap<String, Envelope.SubFolder>();

        static {
            for (Envelope.SubFolder c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SubFolder(String value) {
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
        public static Envelope.SubFolder fromValue(String value) {
            Envelope.SubFolder constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    public enum SubType {

        INVALID("invalid"),
        LOCAL("local"),
        REFLECT("reflect"),
        EVENTS("events"),
        ERRORS("errors"),
        COMMANDS("commands"),
        CONFIG("config"),
        STATE("state"),
        QUERY("query"),
        REPLY("reply"),
        MODEL("model");
        private final String value;
        private final static Map<String, Envelope.SubType> CONSTANTS = new HashMap<String, Envelope.SubType>();

        static {
            for (Envelope.SubType c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SubType(String value) {
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
        public static Envelope.SubType fromValue(String value) {
            Envelope.SubType constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
