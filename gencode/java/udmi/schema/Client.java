
package udmi.schema;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Client {

    @JsonProperty("username")
    public String username;
    @JsonProperty("clientid")
    public String clientid;
    @JsonProperty("roles")
    public List<Role> roles;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.username == null)? 0 :this.username.hashCode()));
        result = ((result* 31)+((this.clientid == null)? 0 :this.clientid.hashCode()));
        result = ((result* 31)+((this.roles == null)? 0 :this.roles.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Client) == false) {
            return false;
        }
        Client rhs = ((Client) other);
        return ((((this.username == rhs.username)||((this.username!= null)&&this.username.equals(rhs.username)))&&((this.clientid == rhs.clientid)||((this.clientid!= null)&&this.clientid.equals(rhs.clientid))))&&((this.roles == rhs.roles)||((this.roles!= null)&&this.roles.equals(rhs.roles))));
    }

}
