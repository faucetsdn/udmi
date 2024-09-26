
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
 * Blobset Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "blobsets",
    "blobs"
})
public class BlobsetConfig {

    /**
     * System Blobsets
     * <p>
     * Predefined system blobsets
     * 
     */
    @JsonProperty("blobsets")
    @JsonPropertyDescription("Predefined system blobsets")
    public BlobsetConfig.SystemBlobsets blobsets;
    @JsonProperty("blobs")
    public HashMap<String, BlobBlobsetConfig> blobs;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.blobs == null)? 0 :this.blobs.hashCode()));
        result = ((result* 31)+((this.blobsets == null)? 0 :this.blobsets.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BlobsetConfig) == false) {
            return false;
        }
        BlobsetConfig rhs = ((BlobsetConfig) other);
        return (((this.blobs == rhs.blobs)||((this.blobs!= null)&&this.blobs.equals(rhs.blobs)))&&((this.blobsets == rhs.blobsets)||((this.blobsets!= null)&&this.blobsets.equals(rhs.blobsets))));
    }


    /**
     * System Blobsets
     * <p>
     * Predefined system blobsets
     * 
     */
    public enum SystemBlobsets {

        IOT_ENDPOINT_CONFIG("_iot_endpoint_config");
        private final java.lang.String value;
        private final static Map<java.lang.String, BlobsetConfig.SystemBlobsets> CONSTANTS = new HashMap<java.lang.String, BlobsetConfig.SystemBlobsets>();

        static {
            for (BlobsetConfig.SystemBlobsets c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SystemBlobsets(java.lang.String value) {
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
        public static BlobsetConfig.SystemBlobsets fromValue(java.lang.String value) {
            BlobsetConfig.SystemBlobsets constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
