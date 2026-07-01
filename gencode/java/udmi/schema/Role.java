
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Role {

    @JsonProperty("rolename")
    public String rolename;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.rolename == null)? 0 :this.rolename.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Role) == false) {
            return false;
        }
        Role rhs = ((Role) other);
        return ((this.rolename == rhs.rolename)||((this.rolename!= null)&&this.rolename.equals(rhs.rolename)));
    }

}
