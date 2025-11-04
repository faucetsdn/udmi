
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Config Cloud Model
 * <p>
 * Information specific to how the device communicates with the cloud.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "static_file"
})
public class ConfigCloudModel {

    /**
     * Config file to use. Within the `config` directory in the device's metadata directory
     * 
     */
    @JsonProperty("static_file")
    @JsonPropertyDescription("Config file to use. Within the `config` directory in the device's metadata directory")
    public String static_file;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.static_file == null)? 0 :this.static_file.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ConfigCloudModel) == false) {
            return false;
        }
        ConfigCloudModel rhs = ((ConfigCloudModel) other);
        return ((this.static_file == rhs.static_file)||((this.static_file!= null)&&this.static_file.equals(rhs.static_file)));
    }

}
