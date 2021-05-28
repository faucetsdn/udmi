
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
 * Cloud configuration metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "auth_type",
    "device_key",
    "is_gateway"
})
@Generated("jsonschema2pojo")
public class File_metadata_cloud_json {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("auth_type")
    public File_metadata_cloud_json.Auth_type auth_type;
    @JsonProperty("device_key")
    public Boolean device_key;
    @JsonProperty("is_gateway")
    public Boolean is_gateway;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.is_gateway == null)? 0 :this.is_gateway.hashCode()));
        result = ((result* 31)+((this.auth_type == null)? 0 :this.auth_type.hashCode()));
        result = ((result* 31)+((this.device_key == null)? 0 :this.device_key.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof File_metadata_cloud_json) == false) {
            return false;
        }
        File_metadata_cloud_json rhs = ((File_metadata_cloud_json) other);
        return ((((this.is_gateway == rhs.is_gateway)||((this.is_gateway!= null)&&this.is_gateway.equals(rhs.is_gateway)))&&((this.auth_type == rhs.auth_type)||((this.auth_type!= null)&&this.auth_type.equals(rhs.auth_type))))&&((this.device_key == rhs.device_key)||((this.device_key!= null)&&this.device_key.equals(rhs.device_key))));
    }

    @Generated("jsonschema2pojo")
    public enum Auth_type {

        ES_256("ES256"),
        ES_256_X_509("ES256_X509"),
        RS_256("RS256"),
        RS_256_X_509("RS256_X509");
        private final String value;
        private final static Map<String, File_metadata_cloud_json.Auth_type> CONSTANTS = new HashMap<String, File_metadata_cloud_json.Auth_type>();

        static {
            for (File_metadata_cloud_json.Auth_type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Auth_type(String value) {
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
        public static File_metadata_cloud_json.Auth_type fromValue(String value) {
            File_metadata_cloud_json.Auth_type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
