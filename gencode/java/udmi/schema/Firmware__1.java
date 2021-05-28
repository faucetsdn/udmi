
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "version"
})
@Generated("jsonschema2pojo")
public class Firmware__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public String version;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Firmware__1) == false) {
            return false;
        }
        Firmware__1 rhs = ((Firmware__1) other);
        return ((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)));
    }

}
