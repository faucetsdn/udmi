
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Mosquitto Client Response
 * <p>
 * Information returned by Mosquitto dynamic security getClient command.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MosquittoClientResponse {

    @JsonProperty("command")
    public String command;
    @JsonProperty("error")
    public String error;
    @JsonProperty("status")
    public Integer status;
    @JsonProperty("data")
    public Data data;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.error == null)? 0 :this.error.hashCode()));
        result = ((result* 31)+((this.data == null)? 0 :this.data.hashCode()));
        result = ((result* 31)+((this.command == null)? 0 :this.command.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MosquittoClientResponse) == false) {
            return false;
        }
        MosquittoClientResponse rhs = ((MosquittoClientResponse) other);
        return (((((this.error == rhs.error)||((this.error!= null)&&this.error.equals(rhs.error)))&&((this.data == rhs.data)||((this.data!= null)&&this.data.equals(rhs.data))))&&((this.command == rhs.command)||((this.command!= null)&&this.command.equals(rhs.command))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
