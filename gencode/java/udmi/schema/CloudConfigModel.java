
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Cloud Config Model
 * <p>
 * Information specific to how the device communicates with the cloud.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "file"
})
@Generated("jsonschema2pojo")
public class CloudConfigModel {

    /**
     * Config file to use, relative to the device's metadata file
     * 
     */
    @JsonProperty("file")
    @JsonPropertyDescription("Config file to use, relative to the device's metadata file")
    public String file;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.file == null)? 0 :this.file.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CloudConfigModel) == false) {
            return false;
        }
        CloudConfigModel rhs = ((CloudConfigModel) other);
        return ((this.file == rhs.file)||((this.file!= null)&&this.file.equals(rhs.file)));
    }

}
