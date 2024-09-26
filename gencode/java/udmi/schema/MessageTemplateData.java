
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Message Template Data
 * <p>
 * Information container for simple template substitution.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp"
})
public class MessageTemplateData {

    /**
     * Message timestamp
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Message timestamp")
    public String timestamp;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MessageTemplateData) == false) {
            return false;
        }
        MessageTemplateData rhs = ((MessageTemplateData) other);
        return ((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp)));
    }

}
