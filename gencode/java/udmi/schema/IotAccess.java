
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
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
    "name",
    "provider",
    "project_id",
    "profile_sec",
    "options"
})
public class IotAccess {

    @JsonProperty("name")
    public String name;
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
    @JsonProperty("profile_sec")
    public Integer profile_sec;
    @JsonProperty("options")
    public String options;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.options == null)? 0 :this.options.hashCode()));
        result = ((result* 31)+((this.provider == null)? 0 :this.provider.hashCode()));
        result = ((result* 31)+((this.project_id == null)? 0 :this.project_id.hashCode()));
        result = ((result* 31)+((this.profile_sec == null)? 0 :this.profile_sec.hashCode()));
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
        return ((((((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name)))&&((this.options == rhs.options)||((this.options!= null)&&this.options.equals(rhs.options))))&&((this.provider == rhs.provider)||((this.provider!= null)&&this.provider.equals(rhs.provider))))&&((this.project_id == rhs.project_id)||((this.project_id!= null)&&this.project_id.equals(rhs.project_id))))&&((this.profile_sec == rhs.profile_sec)||((this.profile_sec!= null)&&this.profile_sec.equals(rhs.profile_sec))));
    }


    /**
     * Iot Provider
     * <p>
     * 
     * 
     */
    public enum IotProvider {

        LOCAL("local"),
        DYNAMIC("dynamic"),
        IMPLICIT("implicit"),
        PUBSUB("pubsub"),
        MQTT("mqtt"),
        GBOS("gbos"),
        GREF("gref"),
        ETCD("etcd"),
        JWT("jwt"),
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
