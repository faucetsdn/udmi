
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "key_format",
    "key_data"
})
public class Credential {

    /**
     * The key type used for cloud communication.
     * 
     */
    @JsonProperty("key_format")
    @JsonPropertyDescription("The key type used for cloud communication.")
    public Credential.Key_format key_format;
    @JsonProperty("key_data")
    public String key_data;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.key_data == null)? 0 :this.key_data.hashCode()));
        result = ((result* 31)+((this.key_format == null)? 0 :this.key_format.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Credential) == false) {
            return false;
        }
        Credential rhs = ((Credential) other);
        return (((this.key_data == rhs.key_data)||((this.key_data!= null)&&this.key_data.equals(rhs.key_data)))&&((this.key_format == rhs.key_format)||((this.key_format!= null)&&this.key_format.equals(rhs.key_format))));
    }


    /**
     * The key type used for cloud communication.
     * 
     */
    public enum Key_format {

        PASSWORD("PASSWORD"),
        ES_256("ES256"),
        ES_256_X_509("ES256_X509"),
        RS_256("RS256"),
        RS_256_X_509("RS256_X509");
        private final String value;
        private final static Map<String, Credential.Key_format> CONSTANTS = new HashMap<String, Credential.Key_format>();

        static {
            for (Credential.Key_format c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Key_format(String value) {
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
        public static Credential.Key_format fromValue(String value) {
            Credential.Key_format constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
