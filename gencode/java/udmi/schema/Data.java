
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Data {

    @JsonProperty("client")
    public Client client;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.client == null)? 0 :this.client.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Data) == false) {
            return false;
        }
        Data rhs = ((Data) other);
        return ((this.client == rhs.client)||((this.client!= null)&&this.client.equals(rhs.client)));
    }

}
