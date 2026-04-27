
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Alarm Ref Discovery
 * <p>
 * Object representation for for a single alarm reference discovery
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "alarm",
    "name",
    "description",
    "category",
    "severity",
    "requires_ack",
    "return_to_normal_event",
    "return_requires_ack"
})
public class AlarmRefDiscovery {

    /**
     * Alarm descriptor for this alarm
     * 
     */
    @JsonProperty("alarm")
    @JsonPropertyDescription("Alarm descriptor for this alarm")
    public String alarm;
    /**
     * Friendly name for the reference, if known
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Friendly name for the reference, if known")
    public String name;
    /**
     * Detailed description of this alarm
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Detailed description of this alarm")
    public String description;
    /**
     * Category that this alarm is classified as
     * 
     */
    @JsonProperty("category")
    @JsonPropertyDescription("Category that this alarm is classified as")
    public String category;
    /**
     * Severity of the alarm
     * 
     */
    @JsonProperty("severity")
    @JsonPropertyDescription("Severity of the alarm")
    public String severity;
    /**
     * Indicates whether or not alarm activation requries acknowledgement.
     * 
     */
    @JsonProperty("requires_ack")
    @JsonPropertyDescription("Indicates whether or not alarm activation requries acknowledgement.")
    public Boolean requires_ack;
    /**
     * Indicates whether or not the alarm sends return-to-normal events.
     * 
     */
    @JsonProperty("return_to_normal_event")
    @JsonPropertyDescription("Indicates whether or not the alarm sends return-to-normal events.")
    public Boolean return_to_normal_event;
    /**
     * Indicates whether or not returning to normal requries acknowledgement.
     * 
     */
    @JsonProperty("return_requires_ack")
    @JsonPropertyDescription("Indicates whether or not returning to normal requries acknowledgement.")
    public Boolean return_requires_ack;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.severity == null)? 0 :this.severity.hashCode()));
        result = ((result* 31)+((this.return_requires_ack == null)? 0 :this.return_requires_ack.hashCode()));
        result = ((result* 31)+((this.return_to_normal_event == null)? 0 :this.return_to_normal_event.hashCode()));
        result = ((result* 31)+((this.alarm == null)? 0 :this.alarm.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.category == null)? 0 :this.category.hashCode()));
        result = ((result* 31)+((this.requires_ack == null)? 0 :this.requires_ack.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AlarmRefDiscovery) == false) {
            return false;
        }
        AlarmRefDiscovery rhs = ((AlarmRefDiscovery) other);
        return (((((((((this.severity == rhs.severity)||((this.severity!= null)&&this.severity.equals(rhs.severity)))&&((this.return_requires_ack == rhs.return_requires_ack)||((this.return_requires_ack!= null)&&this.return_requires_ack.equals(rhs.return_requires_ack))))&&((this.return_to_normal_event == rhs.return_to_normal_event)||((this.return_to_normal_event!= null)&&this.return_to_normal_event.equals(rhs.return_to_normal_event))))&&((this.alarm == rhs.alarm)||((this.alarm!= null)&&this.alarm.equals(rhs.alarm))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.category == rhs.category)||((this.category!= null)&&this.category.equals(rhs.category))))&&((this.requires_ack == rhs.requires_ack)||((this.requires_ack!= null)&&this.requires_ack.equals(rhs.requires_ack))));
    }

}
