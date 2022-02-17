
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


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
@Generated("jsonschema2pojo")
public class Entry {

    /**
     * A one-line representation of the triggering condition.
     * (Required)
     * 
     */
    @JsonProperty("message")
    @JsonPropertyDescription("A one-line representation of the triggering condition.")
    public String message;
    /**
     * An optional be multi-line entry which can include more detail, e.g. a complete program stack-trace.
     * 
     */
    @JsonProperty("detail")
    @JsonPropertyDescription("An optional be multi-line entry which can include more detail, e.g. a complete program stack-trace.")
    public String detail;
    /**
     * A device-specific representation of which sub-system the message comes from. In a Java environment, for example, it would be the fully qualified path name of the Class triggering the message.
     * (Required)
     * 
     */
    @JsonProperty("category")
    @JsonPropertyDescription("A device-specific representation of which sub-system the message comes from. In a Java environment, for example, it would be the fully qualified path name of the Class triggering the message.")
    public String category;
    /**
     * Ttimestamp the condition was triggered, or most recently updated. It might be different than the top-level message `timestamp` if the condition is not checked often or is sticky until it's cleared.
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Ttimestamp the condition was triggered, or most recently updated. It might be different than the top-level message `timestamp` if the condition is not checked often or is sticky until it's cleared.")
    public Date timestamp;
    /**
     * The `level` indicates the severity of the status/log entry https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity
     * (Required)
     * 
     */
    @JsonProperty("level")
    @JsonPropertyDescription("The `level` indicates the severity of the status/log entry")
    public Entry.Level level;

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


    /**
     * The `level` indicates the severity of the status/log entry https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Level {

        ERROR("ERROR"),
        WARNING("WARNING"),
        INFO("INFO"),
        DEBUG("DEBUG");
        private final String value;
        private final static Map<String, Entry.Level> CONSTANTS = new HashMap<String, Entry.Level>();

        static {
            for (Entry.Level c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Level(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Entry.Level fromValue(String value) {
            Entry.Level constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
