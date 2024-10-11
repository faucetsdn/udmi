
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Reply Udmi Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "msg_source",
    "transaction_id"
})
public class ReplyUdmiConfig {

    @JsonProperty("msg_source")
    public String msg_source;
    @JsonProperty("transaction_id")
    public String transaction_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.transaction_id == null)? 0 :this.transaction_id.hashCode()));
        result = ((result* 31)+((this.msg_source == null)? 0 :this.msg_source.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ReplyUdmiConfig) == false) {
            return false;
        }
        ReplyUdmiConfig rhs = ((ReplyUdmiConfig) other);
        return (((this.transaction_id == rhs.transaction_id)||((this.transaction_id!= null)&&this.transaction_id.equals(rhs.transaction_id)))&&((this.msg_source == rhs.msg_source)||((this.msg_source!= null)&&this.msg_source.equals(rhs.msg_source))));
    }

}
