
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    "functions_ver",
    "auth_type",
    "device_key",
    "resource_type",
    "gateway",
    "config",
    "blocked",
    "detail",
    "credentials",
    "updated_time",
    "last_event_time",
    "last_state_time",
    "last_config_time",
    "last_error_time",
    "last_config_ack",
    "num_id",
    "operation",
    "metadata",
    "metadata_str",
    "device_ids"
})
public class CloudModel {

    /**
     * Not published by devices, appended to message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Not published by devices, appended to message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Function version of this request
     * 
     */
    @JsonProperty("functions_ver")
    @JsonPropertyDescription("Function version of this request")
    public Integer functions_ver;
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
     * The cloud resource type
     * 
     */
    @JsonProperty("resource_type")
    @JsonPropertyDescription("The cloud resource type")
    public CloudModel.Resource_type resource_type;
    /**
     * Gateway Model
     * <p>
     * [Gateway Documentation](../docs/specs/gateway.md)
     * 
     */
    @JsonProperty("gateway")
    @JsonPropertyDescription("[Gateway Documentation](../docs/specs/gateway.md)")
    public GatewayModel gateway;
    /**
     * Config Cloud Model
     * <p>
     * Information specific to how the device communicates with the cloud.
     * 
     */
    @JsonProperty("config")
    @JsonPropertyDescription("Information specific to how the device communicates with the cloud.")
    public ConfigCloudModel config;
    @JsonProperty("blocked")
    public Boolean blocked;
    @JsonProperty("detail")
    public java.lang.String detail;
    @JsonProperty("credentials")
    public List<Credential> credentials;
    @JsonProperty("updated_time")
    public Date updated_time;
    @JsonProperty("last_event_time")
    public Date last_event_time;
    @JsonProperty("last_state_time")
    public Date last_state_time;
    @JsonProperty("last_config_time")
    public Date last_config_time;
    @JsonProperty("last_error_time")
    public Date last_error_time;
    @JsonProperty("last_config_ack")
    public Date last_config_ack;
    @JsonProperty("num_id")
    public java.lang.String num_id;
    /**
     * Model Operation
     * <p>
     * 
     * 
     */
    @JsonProperty("operation")
    public CloudModel.ModelOperation operation;
    @JsonProperty("metadata")
    public Map<String, String> metadata;
    /**
     * Strigified version of the metadata object, used for internal backend processing
     * 
     */
    @JsonProperty("metadata_str")
    @JsonPropertyDescription("Strigified version of the metadata object, used for internal backend processing")
    public java.lang.String metadata_str;
    /**
     * If operating on the entire registry, then this contains results for multiple devices (batched operation).
     * 
     */
    @JsonProperty("device_ids")
    @JsonPropertyDescription("If operating on the entire registry, then this contains results for multiple devices (batched operation).")
    public Map<String, CloudModel> device_ids;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.updated_time == null)? 0 :this.updated_time.hashCode()));
        result = ((result* 31)+((this.auth_type == null)? 0 :this.auth_type.hashCode()));
        result = ((result* 31)+((this.device_key == null)? 0 :this.device_key.hashCode()));
        result = ((result* 31)+((this.metadata == null)? 0 :this.metadata.hashCode()));
        result = ((result* 31)+((this.last_event_time == null)? 0 :this.last_event_time.hashCode()));
        result = ((result* 31)+((this.last_config_time == null)? 0 :this.last_config_time.hashCode()));
        result = ((result* 31)+((this.credentials == null)? 0 :this.credentials.hashCode()));
        result = ((result* 31)+((this.last_state_time == null)? 0 :this.last_state_time.hashCode()));
        result = ((result* 31)+((this.functions_ver == null)? 0 :this.functions_ver.hashCode()));
        result = ((result* 31)+((this.resource_type == null)? 0 :this.resource_type.hashCode()));
        result = ((result* 31)+((this.num_id == null)? 0 :this.num_id.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.blocked == null)? 0 :this.blocked.hashCode()));
        result = ((result* 31)+((this.last_error_time == null)? 0 :this.last_error_time.hashCode()));
        result = ((result* 31)+((this.metadata_str == null)? 0 :this.metadata_str.hashCode()));
        result = ((result* 31)+((this.detail == null)? 0 :this.detail.hashCode()));
        result = ((result* 31)+((this.device_ids == null)? 0 :this.device_ids.hashCode()));
        result = ((result* 31)+((this.config == null)? 0 :this.config.hashCode()));
        result = ((result* 31)+((this.last_config_ack == null)? 0 :this.last_config_ack.hashCode()));
        result = ((result* 31)+((this.operation == null)? 0 :this.operation.hashCode()));
        result = ((result* 31)+((this.gateway == null)? 0 :this.gateway.hashCode()));
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
        return (((((((((((((((((((((((this.updated_time == rhs.updated_time)||((this.updated_time!= null)&&this.updated_time.equals(rhs.updated_time)))&&((this.auth_type == rhs.auth_type)||((this.auth_type!= null)&&this.auth_type.equals(rhs.auth_type))))&&((this.device_key == rhs.device_key)||((this.device_key!= null)&&this.device_key.equals(rhs.device_key))))&&((this.metadata == rhs.metadata)||((this.metadata!= null)&&this.metadata.equals(rhs.metadata))))&&((this.last_event_time == rhs.last_event_time)||((this.last_event_time!= null)&&this.last_event_time.equals(rhs.last_event_time))))&&((this.last_config_time == rhs.last_config_time)||((this.last_config_time!= null)&&this.last_config_time.equals(rhs.last_config_time))))&&((this.credentials == rhs.credentials)||((this.credentials!= null)&&this.credentials.equals(rhs.credentials))))&&((this.last_state_time == rhs.last_state_time)||((this.last_state_time!= null)&&this.last_state_time.equals(rhs.last_state_time))))&&((this.functions_ver == rhs.functions_ver)||((this.functions_ver!= null)&&this.functions_ver.equals(rhs.functions_ver))))&&((this.resource_type == rhs.resource_type)||((this.resource_type!= null)&&this.resource_type.equals(rhs.resource_type))))&&((this.num_id == rhs.num_id)||((this.num_id!= null)&&this.num_id.equals(rhs.num_id))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.blocked == rhs.blocked)||((this.blocked!= null)&&this.blocked.equals(rhs.blocked))))&&((this.last_error_time == rhs.last_error_time)||((this.last_error_time!= null)&&this.last_error_time.equals(rhs.last_error_time))))&&((this.metadata_str == rhs.metadata_str)||((this.metadata_str!= null)&&this.metadata_str.equals(rhs.metadata_str))))&&((this.detail == rhs.detail)||((this.detail!= null)&&this.detail.equals(rhs.detail))))&&((this.device_ids == rhs.device_ids)||((this.device_ids!= null)&&this.device_ids.equals(rhs.device_ids))))&&((this.config == rhs.config)||((this.config!= null)&&this.config.equals(rhs.config))))&&((this.last_config_ack == rhs.last_config_ack)||((this.last_config_ack!= null)&&this.last_config_ack.equals(rhs.last_config_ack))))&&((this.operation == rhs.operation)||((this.operation!= null)&&this.operation.equals(rhs.operation))))&&((this.gateway == rhs.gateway)||((this.gateway!= null)&&this.gateway.equals(rhs.gateway))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }


    /**
     * The key type used for cloud communication.
     * 
     */
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


    /**
     * Model Operation
     * <p>
     * 
     * 
     */
    public enum ModelOperation {

        ERROR("ERROR"),
        CREATE("CREATE"),
        READ("READ"),
        REPLY("REPLY"),
        UPDATE("UPDATE"),
        DELETE("DELETE"),
        MODIFY("MODIFY"),
        PREVIEW("PREVIEW"),
        BIND("BIND"),
        UNBIND("UNBIND"),
        BOUND("BOUND"),
        ALLOW("ALLOW"),
        BLOCK("BLOCK");
        private final java.lang.String value;
        private final static Map<java.lang.String, CloudModel.ModelOperation> CONSTANTS = new HashMap<java.lang.String, CloudModel.ModelOperation>();

        static {
            for (CloudModel.ModelOperation c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        ModelOperation(java.lang.String value) {
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
        public static CloudModel.ModelOperation fromValue(java.lang.String value) {
            CloudModel.ModelOperation constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }


    /**
     * The cloud resource type
     * 
     */
    public enum Resource_type {

        DIRECT("DIRECT"),
        GATEWAY("GATEWAY"),
        FUNNEL("FUNNEL"),
        PROXIED("PROXIED"),
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
