
package udmi.schema;

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
    "sequence_name",
    "transaction_id",
    "config_base",
    "endpoint_type"
})
public class TestingSystemConfig {

    /**
     * The sequence name currently being tested (for debug logging)
     * 
     */
    @JsonProperty("sequence_name")
    @JsonPropertyDescription("The sequence name currently being tested (for debug logging)")
    public String sequence_name;
    /**
     * The transaction id used to generate this config update
     * 
     */
    @JsonProperty("transaction_id")
    @JsonPropertyDescription("The transaction id used to generate this config update")
    public String transaction_id;
    /**
     * The configuration version that this update was based on
     * 
     */
    @JsonProperty("config_base")
    @JsonPropertyDescription("The configuration version that this update was based on")
    public Integer config_base;
    /**
     * Designator for the kind of endpoint being used for this test
     * 
     */
    @JsonProperty("endpoint_type")
    @JsonPropertyDescription("Designator for the kind of endpoint being used for this test")
    public String endpoint_type;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.transaction_id == null)? 0 :this.transaction_id.hashCode()));
        result = ((result* 31)+((this.endpoint_type == null)? 0 :this.endpoint_type.hashCode()));
        result = ((result* 31)+((this.config_base == null)? 0 :this.config_base.hashCode()));
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
        return (((((this.transaction_id == rhs.transaction_id)||((this.transaction_id!= null)&&this.transaction_id.equals(rhs.transaction_id)))&&((this.endpoint_type == rhs.endpoint_type)||((this.endpoint_type!= null)&&this.endpoint_type.equals(rhs.endpoint_type))))&&((this.config_base == rhs.config_base)||((this.config_base!= null)&&this.config_base.equals(rhs.config_base))))&&((this.sequence_name == rhs.sequence_name)||((this.sequence_name!= null)&&this.sequence_name.equals(rhs.sequence_name))));
    }

}
