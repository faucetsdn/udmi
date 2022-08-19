
package udmi.schema;

import java.net.URI;
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
 * Blob Blobset Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "phase",
    "content_type",
    "base64",
    "url",
    "sha256"
})
@Generated("jsonschema2pojo")
public class BlobBlobsetConfig {

    /**
     * BlobPhase
     * <p>
     * Phase for the management of a configuration blob.
     * 
     */
    @JsonProperty("phase")
    @JsonPropertyDescription("Phase for the management of a configuration blob.")
    public BlobBlobsetConfig.BlobPhase phase;
    @JsonProperty("content_type")
    public String content_type;
    @JsonProperty("base64")
    public String base64;
    @JsonProperty("url")
    public URI url;
    /**
     * Expected hash of the oded content
     * 
     */
    @JsonProperty("sha256")
    @JsonPropertyDescription("Expected hash of the oded content")
    public String sha256;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.phase == null)? 0 :this.phase.hashCode()));
        result = ((result* 31)+((this.base64 == null)? 0 :this.base64 .hashCode()));
        result = ((result* 31)+((this.content_type == null)? 0 :this.content_type.hashCode()));
        result = ((result* 31)+((this.sha256 == null)? 0 :this.sha256 .hashCode()));
        result = ((result* 31)+((this.url == null)? 0 :this.url.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BlobBlobsetConfig) == false) {
            return false;
        }
        BlobBlobsetConfig rhs = ((BlobBlobsetConfig) other);
        return ((((((this.phase == rhs.phase)||((this.phase!= null)&&this.phase.equals(rhs.phase)))&&((this.base64 == rhs.base64)||((this.base64 != null)&&this.base64 .equals(rhs.base64))))&&((this.content_type == rhs.content_type)||((this.content_type!= null)&&this.content_type.equals(rhs.content_type))))&&((this.sha256 == rhs.sha256)||((this.sha256 != null)&&this.sha256 .equals(rhs.sha256))))&&((this.url == rhs.url)||((this.url!= null)&&this.url.equals(rhs.url))));
    }


    /**
     * BlobPhase
     * <p>
     * Phase for the management of a configuration blob.
     * 
     */
    @Generated("jsonschema2pojo")
    public enum BlobPhase {

        INITIAL("initial"),
        APPLY("apply"),
        FINAL("final");
        private final String value;
        private final static Map<String, BlobBlobsetConfig.BlobPhase> CONSTANTS = new HashMap<String, BlobBlobsetConfig.BlobPhase>();

        static {
            for (BlobBlobsetConfig.BlobPhase c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        BlobPhase(String value) {
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
        public static BlobBlobsetConfig.BlobPhase fromValue(String value) {
            BlobBlobsetConfig.BlobPhase constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
