
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Testing System Config
 * <p>
 * Configuration parameters for device-under-test
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "sequence_name"
})
@Generated("jsonschema2pojo")
public class TestingSystemConfig {

    /**
     * The sequence name currently being tested (for debug logging)
     * 
     */
    @JsonProperty("sequence_name")
    @JsonPropertyDescription("The sequence name currently being tested (for debug logging)")
    public String sequence_name;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sequence_name == null)? 0 :this.sequence_name.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TestingSystemConfig) == false) {
            return false;
        }
        TestingSystemConfig rhs = ((TestingSystemConfig) other);
        return ((this.sequence_name == rhs.sequence_name)||((this.sequence_name!= null)&&this.sequence_name.equals(rhs.sequence_name)));
    }

}
