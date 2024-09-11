
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Parent device to which the device is physically connected
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "family"
})
@Generated("jsonschema2pojo")
public class Parent {

    /**
     * ID of the parent device to which the device is physically connected
     * 
     */
    @JsonProperty("id")
    @JsonPropertyDescription("ID of the parent device to which the device is physically connected")
    public String id;
    /**
     * Connection family/protocol of the parent device to which the device is physically connected
     * 
     */
    @JsonProperty("family")
    @JsonPropertyDescription("Connection family/protocol of the parent device to which the device is physically connected")
    public String family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Parent) == false) {
            return false;
        }
        Parent rhs = ((Parent) other);
        return (((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family)))&&((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id))));
    }

}
