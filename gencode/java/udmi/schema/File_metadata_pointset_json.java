
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "points"
})
@Generated("jsonschema2pojo")
public class File_metadata_pointset_json {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("points")
    public Object points;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.points == null)? 0 :this.points.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof File_metadata_pointset_json) == false) {
            return false;
        }
        File_metadata_pointset_json rhs = ((File_metadata_pointset_json) other);
        return ((this.points == rhs.points)||((this.points!= null)&&this.points.equals(rhs.points)));
    }

}
