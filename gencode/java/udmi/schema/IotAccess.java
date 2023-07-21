
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
 * Iot Access
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "provider",
    "project_id"
})
@Generated("jsonschema2pojo")
public class IotAccess {

    /**
     * Iot Provider
     * <p>
     * 
     * 
     */
    @JsonProperty("provider")
    public IotAccess.IotProvider provider;
    @JsonProperty("project_id")
    public String project_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.provider == null)? 0 :this.provider.hashCode()));
        result = ((result* 31)+((this.project_id == null)? 0 :this.project_id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof IotAccess) == false) {
            return false;
        }
        IotAccess rhs = ((IotAccess) other);
        return (((this.provider == rhs.provider)||((this.provider!= null)&&this.provider.equals(rhs.provider)))&&((this.project_id == rhs.project_id)||((this.project_id!= null)&&this.project_id.equals(rhs.project_id))));
    }


    /**
     * Iot Provider
     * <p>
     * 
     * 
     */
    @Generated("jsonschema2pojo")
    public enum IotProvider {

        LOCAL("local"),
        DYNAMIC("dynamic"),
        IMPLICIT("implicit"),
        GCP_NATIVE("gcp_native"),
        GCP("gcp"),
        CLEARBLADE_NATIVE("clearblade_native"),
        CLEARBLADE("clearblade");
        private final String value;
        private final static Map<String, IotAccess.IotProvider> CONSTANTS = new HashMap<String, IotAccess.IotProvider>();

        static {
            for (IotAccess.IotProvider c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        IotProvider(String value) {
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
        public static IotAccess.IotProvider fromValue(String value) {
            IotAccess.IotProvider constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
