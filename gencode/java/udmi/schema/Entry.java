
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Entry
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "message",
    "detail",
    "category",
    "timestamp",
    "level"
})
public class Entry {

    /**
     * A human-readable one-line description of the entry
     * (Required)
     * 
     */
    @JsonProperty("message")
    @JsonPropertyDescription("A human-readable one-line description of the entry")
    public String message;
    /**
     * An optional extensive entry which can include more detail, e.g. a complete program stack-trace
     * 
     */
    @JsonProperty("detail")
    @JsonPropertyDescription("An optional extensive entry which can include more detail, e.g. a complete program stack-trace")
    public String detail;
    /**
     * Category
     * <p>
     * Auto-generated category mappings from bin/gencode_categories.
     * (Required)
     * 
     */
    @JsonProperty("category")
    public String category;
    /**
     * FC 3339 UTC timestamp the condition was triggered, or most recently updated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("FC 3339 UTC timestamp the condition was triggered, or most recently updated")
    public Date timestamp;
    /**
     * The status `level` should conform to the numerical [Stackdriver LogEntry](https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity) levels. The `DEFAULT` value of 0 is not allowed (lowest value is 100, maximum 800).https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity
     * (Required)
     * 
     */
    @JsonProperty("level")
    @JsonPropertyDescription("The status `level` should conform to the numerical [Stackdriver LogEntry](https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity) levels. The `DEFAULT` value of 0 is not allowed (lowest value is 100, maximum 800).")
    public Integer level;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.detail == null)? 0 :this.detail.hashCode()));
        result = ((result* 31)+((this.message == null)? 0 :this.message.hashCode()));
        result = ((result* 31)+((this.category == null)? 0 :this.category.hashCode()));
        result = ((result* 31)+((this.level == null)? 0 :this.level.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Entry) == false) {
            return false;
        }
        Entry rhs = ((Entry) other);
        return ((((((this.detail == rhs.detail)||((this.detail!= null)&&this.detail.equals(rhs.detail)))&&((this.message == rhs.message)||((this.message!= null)&&this.message.equals(rhs.message))))&&((this.category == rhs.category)||((this.category!= null)&&this.category.equals(rhs.category))))&&((this.level == rhs.level)||((this.level!= null)&&this.level.equals(rhs.level))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
