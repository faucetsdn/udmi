
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Units {

    @JsonProperty("key")
    public String key;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.key == null)? 0 :this.key.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Units) == false) {
            return false;
        }
        Units rhs = ((Units) other);
        return ((this.key == rhs.key)||((this.key!= null)&&this.key.equals(rhs.key)));
    }

}
