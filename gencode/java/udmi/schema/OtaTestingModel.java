
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Ota Testing Model
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "blob_key",
    "url",
    "sha256",
    "version",
    "test_type"
})
public class OtaTestingModel {

    /**
     * Blob key for the payload
     * 
     */
    @JsonProperty("blob_key")
    @JsonPropertyDescription("Blob key for the payload")
    public String blob_key;
    /**
     * URL for OTA update
     * 
     */
    @JsonProperty("url")
    @JsonPropertyDescription("URL for OTA update")
    public String url;
    /**
     * SHA256 hash of the payload
     * 
     */
    @JsonProperty("sha256")
    @JsonPropertyDescription("SHA256 hash of the payload")
    public String sha256;
    /**
     * Expected software version
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Expected software version")
    public String version;
    /**
     * Type of test to run for this payload. 'happy' expects successful application and version update. 'bad_hash' expects error state due to hash mismatch.
     * 
     */
    @JsonProperty("test_type")
    @JsonPropertyDescription("Type of test to run for this payload. 'happy' expects successful application and version update. 'bad_hash' expects error state due to hash mismatch.")
    public OtaTestingModel.Test_type test_type;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.blob_key == null)? 0 :this.blob_key.hashCode()));
        result = ((result* 31)+((this.sha256 == null)? 0 :this.sha256 .hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.url == null)? 0 :this.url.hashCode()));
        result = ((result* 31)+((this.test_type == null)? 0 :this.test_type.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OtaTestingModel) == false) {
            return false;
        }
        OtaTestingModel rhs = ((OtaTestingModel) other);
        return ((((((this.blob_key == rhs.blob_key)||((this.blob_key!= null)&&this.blob_key.equals(rhs.blob_key)))&&((this.sha256 == rhs.sha256)||((this.sha256 != null)&&this.sha256 .equals(rhs.sha256))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.url == rhs.url)||((this.url!= null)&&this.url.equals(rhs.url))))&&((this.test_type == rhs.test_type)||((this.test_type!= null)&&this.test_type.equals(rhs.test_type))));
    }


    /**
     * Type of test to run for this payload. 'happy' expects successful application and version update. 'bad_hash' expects error state due to hash mismatch.
     * 
     */
    public enum Test_type {

        HAPPY("happy"),
        BAD_HASH("bad_hash");
        private final String value;
        private final static Map<String, OtaTestingModel.Test_type> CONSTANTS = new HashMap<String, OtaTestingModel.Test_type>();

        static {
            for (OtaTestingModel.Test_type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Test_type(String value) {
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
        public static OtaTestingModel.Test_type fromValue(String value) {
            OtaTestingModel.Test_type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
