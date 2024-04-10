
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
 * Iot Data
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "provider"
})
@Generated("jsonschema2pojo")
public class IotData {

    @JsonProperty("name")
    public String name;
    /**
     * Data Provider
     * <p>
     * 
     * 
     */
    @JsonProperty("provider")
    public IotData.DataProvider provider;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.provider == null)? 0 :this.provider.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof IotData) == false) {
            return false;
        }
        IotData rhs = ((IotData) other);
        return (((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name)))&&((this.provider == rhs.provider)||((this.provider!= null)&&this.provider.equals(rhs.provider))));
    }


    /**
     * Data Provider
     * <p>
     * 
     * 
     */
    @Generated("jsonschema2pojo")
    public enum DataProvider {

        ETCD("etcd");
        private final String value;
        private final static Map<String, IotData.DataProvider> CONSTANTS = new HashMap<String, IotData.DataProvider>();

        static {
            for (IotData.DataProvider c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        DataProvider(String value) {
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
        public static IotData.DataProvider fromValue(String value) {
            IotData.DataProvider constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
