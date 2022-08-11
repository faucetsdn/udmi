
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Testing Config
 * <p>
 * Configuration parameters for device-under-test
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "sqeuence_name"
})
@Generated("jsonschema2pojo")
public class TestingConfig {

    /**
     * The sequence name currently being tested (for debug logging)
     * 
     */
    @JsonProperty("sqeuence_name")
    @JsonPropertyDescription("The sequence name currently being tested (for debug logging)")
    public String sqeuence_name;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sqeuence_name == null)? 0 :this.sqeuence_name.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TestingConfig) == false) {
            return false;
        }
        TestingConfig rhs = ((TestingConfig) other);
        return ((this.sqeuence_name == rhs.sqeuence_name)||((this.sqeuence_name!= null)&&this.sqeuence_name.equals(rhs.sqeuence_name)));
    }

}
