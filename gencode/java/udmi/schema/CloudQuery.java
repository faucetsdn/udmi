
package udmi.schema;

import java.util.Date;
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
 * Cloud Query
 * <p>
 * Information specific to how the device communicates with the cloud.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "generation",
    "depth"
})
@Generated("jsonschema2pojo")
public class CloudQuery {

    @JsonProperty("timestamp")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * generational marker for this query
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("generational marker for this query")
    public Date generation;
    @JsonProperty("depth")
    public CloudQuery.Depth depth;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.depth == null)? 0 :this.depth.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CloudQuery) == false) {
            return false;
        }
        CloudQuery rhs = ((CloudQuery) other);
        return (((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.depth == rhs.depth)||((this.depth!= null)&&this.depth.equals(rhs.depth))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

    @Generated("jsonschema2pojo")
    public enum Depth {

        REGISTRIES("registries"),
        DEVICES("devices"),
        DETAILS("details");
        private final String value;
        private final static Map<String, CloudQuery.Depth> CONSTANTS = new HashMap<String, CloudQuery.Depth>();

        static {
            for (CloudQuery.Depth c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Depth(String value) {
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
        public static CloudQuery.Depth fromValue(String value) {
            CloudQuery.Depth constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
