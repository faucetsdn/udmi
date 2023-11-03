
package udmi.schema;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Cloud Model
 * <p>
 * Information specific to how the device communicates with the cloud.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "auth_type",
    "device_key",
    "resource_type",
    "blocked",
    "credentials",
    "last_event_time",
    "last_config_ack",
    "num_id",
    "operation",
    "metadata",
    "device_ids"
})
@Generated("jsonschema2pojo")
public class CloudModel {

    /**
     * RFC 3339 Timestamp the state payload was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 Timestamp the state payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * The key type used for cloud communication.
     * 
     */
    @JsonProperty("auth_type")
    @JsonPropertyDescription("The key type used for cloud communication.")
    public CloudModel.Auth_type auth_type;
    /**
     * Indicates if the device private key is provided in the site model.
     * 
     */
    @JsonProperty("device_key")
    @JsonPropertyDescription("Indicates if the device private key is provided in the site model.")
    public Boolean device_key;
    /**
     * The resource type of this model
     * 
     */
    @JsonProperty("resource_type")
    @JsonPropertyDescription("The resource type of this model")
    public CloudModel.Resource_type resource_type;
    @JsonProperty("blocked")
    public Boolean blocked;
    @JsonProperty("credentials")
    public List<Credential> credentials = new ArrayList<Credential>();
    @JsonProperty("last_event_time")
    public Date last_event_time;
    @JsonProperty("last_config_ack")
    public Date last_config_ack;
    @JsonProperty("num_id")
    public java.lang.String num_id;
    @JsonProperty("operation")
    public CloudModel.Operation operation;
    @JsonProperty("metadata")
    public Map<String, String> metadata;
    /**
     * If operating on the entire registry, then this manifests as a map of devices not just one device.
     * 
     */
    @JsonProperty("device_ids")
    @JsonPropertyDescription("If operating on the entire registry, then this manifests as a map of devices not just one device.")
    public Map<String, CloudModel> device_ids;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.auth_type == null)? 0 :this.auth_type.hashCode()));
        result = ((result* 31)+((this.device_key == null)? 0 :this.device_key.hashCode()));
        result = ((result* 31)+((this.metadata == null)? 0 :this.metadata.hashCode()));
        result = ((result* 31)+((this.last_event_time == null)? 0 :this.last_event_time.hashCode()));
        result = ((result* 31)+((this.credentials == null)? 0 :this.credentials.hashCode()));
        result = ((result* 31)+((this.resource_type == null)? 0 :this.resource_type.hashCode()));
        result = ((result* 31)+((this.num_id == null)? 0 :this.num_id.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.blocked == null)? 0 :this.blocked.hashCode()));
        result = ((result* 31)+((this.device_ids == null)? 0 :this.device_ids.hashCode()));
        result = ((result* 31)+((this.last_config_ack == null)? 0 :this.last_config_ack.hashCode()));
        result = ((result* 31)+((this.operation == null)? 0 :this.operation.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CloudModel) == false) {
            return false;
        }
        CloudModel rhs = ((CloudModel) other);
        return ((((((((((((((this.auth_type == rhs.auth_type)||((this.auth_type!= null)&&this.auth_type.equals(rhs.auth_type)))&&((this.device_key == rhs.device_key)||((this.device_key!= null)&&this.device_key.equals(rhs.device_key))))&&((this.metadata == rhs.metadata)||((this.metadata!= null)&&this.metadata.equals(rhs.metadata))))&&((this.last_event_time == rhs.last_event_time)||((this.last_event_time!= null)&&this.last_event_time.equals(rhs.last_event_time))))&&((this.credentials == rhs.credentials)||((this.credentials!= null)&&this.credentials.equals(rhs.credentials))))&&((this.resource_type == rhs.resource_type)||((this.resource_type!= null)&&this.resource_type.equals(rhs.resource_type))))&&((this.num_id == rhs.num_id)||((this.num_id!= null)&&this.num_id.equals(rhs.num_id))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.blocked == rhs.blocked)||((this.blocked!= null)&&this.blocked.equals(rhs.blocked))))&&((this.device_ids == rhs.device_ids)||((this.device_ids!= null)&&this.device_ids.equals(rhs.device_ids))))&&((this.last_config_ack == rhs.last_config_ack)||((this.last_config_ack!= null)&&this.last_config_ack.equals(rhs.last_config_ack))))&&((this.operation == rhs.operation)||((this.operation!= null)&&this.operation.equals(rhs.operation))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }


    /**
     * The key type used for cloud communication.
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Auth_type {

        ES_256("ES256"),
        ES_256_X_509("ES256_X509"),
        RS_256("RS256"),
        RS_256_X_509("RS256_X509");
        private final java.lang.String value;
        private final static Map<java.lang.String, CloudModel.Auth_type> CONSTANTS = new HashMap<java.lang.String, CloudModel.Auth_type>();

        static {
            for (CloudModel.Auth_type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Auth_type(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static CloudModel.Auth_type fromValue(java.lang.String value) {
            CloudModel.Auth_type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    @Generated("jsonschema2pojo")
    public enum Operation {

        FETCH("FETCH"),
        CREATE("CREATE"),
        UPDATE("UPDATE"),
        DELETE("DELETE"),
        BIND("BIND"),
        BLOCK("BLOCK");
        private final java.lang.String value;
        private final static Map<java.lang.String, CloudModel.Operation> CONSTANTS = new HashMap<java.lang.String, CloudModel.Operation>();

        static {
            for (CloudModel.Operation c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Operation(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static CloudModel.Operation fromValue(java.lang.String value) {
            CloudModel.Operation constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }


    /**
     * The resource type of this model
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Resource_type {

        DEVICE("DEVICE"),
        GATEWAY("GATEWAY"),
        REGISTRY("REGISTRY");
        private final java.lang.String value;
        private final static Map<java.lang.String, CloudModel.Resource_type> CONSTANTS = new HashMap<java.lang.String, CloudModel.Resource_type>();

        static {
            for (CloudModel.Resource_type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Resource_type(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static CloudModel.Resource_type fromValue(java.lang.String value) {
            CloudModel.Resource_type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
