
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "subFolder",
    "subType"
})
@Generated("jsonschema2pojo")
public class Target {

    /**
     * Original folder (system, pointset, etc...) of validated message
     * 
     */
    @JsonProperty("subFolder")
    @JsonPropertyDescription("Original folder (system, pointset, etc...) of validated message")
    public String subFolder;
    /**
     * Original subType (config, event, etc...) of validated message
     * 
     */
    @JsonProperty("subType")
    @JsonPropertyDescription("Original subType (config, event, etc...) of validated message")
    public String subType;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.subFolder == null)? 0 :this.subFolder.hashCode()));
        result = ((result* 31)+((this.subType == null)? 0 :this.subType.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Target) == false) {
            return false;
        }
        Target rhs = ((Target) other);
        return (((this.subFolder == rhs.subFolder)||((this.subFolder!= null)&&this.subFolder.equals(rhs.subFolder)))&&((this.subType == rhs.subType)||((this.subType!= null)&&this.subType.equals(rhs.subType))));
    }

}
