
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
    "subFolder",
    "subType"
})
@Generated("jsonschema2pojo")
public class Envelope {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("deviceId")
    public String deviceId;
    /**
     * 
     * (Required)
     * 
     */
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
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("projectId")
    public String projectId;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("subFolder")
    public Envelope.SubFolder subFolder;
    @JsonProperty("subType")
    public Envelope.SubType subType;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.deviceRegistryLocation == null)? 0 :this.deviceRegistryLocation.hashCode()));
        result = ((result* 31)+((this.deviceNumId == null)? 0 :this.deviceNumId.hashCode()));
        result = ((result* 31)+((this.subFolder == null)? 0 :this.subFolder.hashCode()));
        result = ((result* 31)+((this.deviceRegistryId == null)? 0 :this.deviceRegistryId.hashCode()));
        result = ((result* 31)+((this.subType == null)? 0 :this.subType.hashCode()));
        result = ((result* 31)+((this.deviceId == null)? 0 :this.deviceId.hashCode()));
        result = ((result* 31)+((this.projectId == null)? 0 :this.projectId.hashCode()));
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
        return ((((((((this.deviceRegistryLocation == rhs.deviceRegistryLocation)||((this.deviceRegistryLocation!= null)&&this.deviceRegistryLocation.equals(rhs.deviceRegistryLocation)))&&((this.deviceNumId == rhs.deviceNumId)||((this.deviceNumId!= null)&&this.deviceNumId.equals(rhs.deviceNumId))))&&((this.subFolder == rhs.subFolder)||((this.subFolder!= null)&&this.subFolder.equals(rhs.subFolder))))&&((this.deviceRegistryId == rhs.deviceRegistryId)||((this.deviceRegistryId!= null)&&this.deviceRegistryId.equals(rhs.deviceRegistryId))))&&((this.subType == rhs.subType)||((this.subType!= null)&&this.subType.equals(rhs.subType))))&&((this.deviceId == rhs.deviceId)||((this.deviceId!= null)&&this.deviceId.equals(rhs.deviceId))))&&((this.projectId == rhs.projectId)||((this.projectId!= null)&&this.projectId.equals(rhs.projectId))));
    }

    @Generated("jsonschema2pojo")
    public enum SubFolder {

        UPDATE("update"),
        AUDIT("audit"),
        TESTING("testing"),
        DISCOVERY("discovery"),
        SYSTEM("system"),
        GATEWAY("gateway"),
        SWARM("swarm"),
        LOCALNET("localnet"),
        POINTSET("pointset"),
        BLOBSET("blobset");
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

    @Generated("jsonschema2pojo")
    public enum SubType {

        EVENT("event"),
        COMMAND("command"),
        STATE("state"),
        CONFIG("config"),
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
