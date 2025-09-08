
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pubber Options
 * <p>
 * Pubber runtime options
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "fixedSampleRate",
    "noHardware",
    "noConfigAck",
    "noPersist",
    "noLastStart",
    "noLastConfig",
    "badCategory",
    "badVersion",
    "badAddr",
    "noProxy",
    "extraDevice",
    "barfConfig",
    "messageTrace",
    "extraPoint",
    "configStateDelay",
    "missingPoint",
    "extraField",
    "emptyMissing",
    "redirectRegistry",
    "msTimestamp",
    "smokeCheck",
    "skewClock",
    "noPointState",
    "noState",
    "noStatus",
    "noFolder",
    "badLevel",
    "spamState",
    "tweakState",
    "badState",
    "baseState",
    "dupeState",
    "noLog",
    "featureEnableSwap",
    "disableWriteback",
    "noWriteback",
    "fixedLogLevel",
    "fastWrite",
    "delayWrite",
    "softwareFirmwareValue"
})
public class PubberOptions {

    @JsonProperty("fixedSampleRate")
    public Integer fixedSampleRate;
    @JsonProperty("noHardware")
    public Boolean noHardware;
    @JsonProperty("noConfigAck")
    public Boolean noConfigAck;
    @JsonProperty("noPersist")
    public Boolean noPersist;
    @JsonProperty("noLastStart")
    public Boolean noLastStart;
    @JsonProperty("noLastConfig")
    public Boolean noLastConfig;
    @JsonProperty("badCategory")
    public Boolean badCategory;
    @JsonProperty("badVersion")
    public Boolean badVersion;
    @JsonProperty("badAddr")
    public Boolean badAddr;
    @JsonProperty("noProxy")
    public Boolean noProxy;
    @JsonProperty("extraDevice")
    public Boolean extraDevice;
    @JsonProperty("barfConfig")
    public Boolean barfConfig;
    @JsonProperty("messageTrace")
    public Boolean messageTrace;
    @JsonProperty("extraPoint")
    public String extraPoint;
    @JsonProperty("configStateDelay")
    public Boolean configStateDelay;
    @JsonProperty("missingPoint")
    public String missingPoint;
    @JsonProperty("extraField")
    public String extraField;
    @JsonProperty("emptyMissing")
    public Boolean emptyMissing;
    @JsonProperty("redirectRegistry")
    public String redirectRegistry;
    @JsonProperty("msTimestamp")
    public Boolean msTimestamp;
    @JsonProperty("smokeCheck")
    public Boolean smokeCheck;
    @JsonProperty("skewClock")
    public Boolean skewClock;
    @JsonProperty("noPointState")
    public Boolean noPointState;
    @JsonProperty("noState")
    public Boolean noState;
    @JsonProperty("noStatus")
    public Boolean noStatus;
    @JsonProperty("noFolder")
    public Boolean noFolder;
    @JsonProperty("badLevel")
    public Boolean badLevel;
    @JsonProperty("spamState")
    public Boolean spamState;
    @JsonProperty("tweakState")
    public Boolean tweakState;
    @JsonProperty("badState")
    public Boolean badState;
    @JsonProperty("baseState")
    public Boolean baseState;
    @JsonProperty("dupeState")
    public Boolean dupeState;
    @JsonProperty("noLog")
    public Boolean noLog;
    @JsonProperty("featureEnableSwap")
    public Boolean featureEnableSwap;
    /**
     * Disable writeback, equivelant to marking all points as unwriteable
     * 
     */
    @JsonProperty("disableWriteback")
    @JsonPropertyDescription("Disable writeback, equivelant to marking all points as unwriteable")
    public Boolean disableWriteback;
    /**
     * Removes writeback functionality, mimicking a device without Writeback support
     * 
     */
    @JsonProperty("noWriteback")
    @JsonPropertyDescription("Removes writeback functionality, mimicking a device without Writeback support")
    public Boolean noWriteback;
    @JsonProperty("fixedLogLevel")
    public Integer fixedLogLevel;
    /**
     * If true, the pubber will simulate a fast writeback operations.
     * 
     */
    @JsonProperty("fastWrite")
    @JsonPropertyDescription("If true, the pubber will simulate a fast writeback operations.")
    public Boolean fastWrite;
    /**
     * If true, the pubber will simulate a delayed writeback operations, leading to slow writeback without an intermediate updating state.
     * 
     */
    @JsonProperty("delayWrite")
    @JsonPropertyDescription("If true, the pubber will simulate a delayed writeback operations, leading to slow writeback without an intermediate updating state.")
    public Boolean delayWrite;
    @JsonProperty("softwareFirmwareValue")
    public String softwareFirmwareValue;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.skewClock == null)? 0 :this.skewClock.hashCode()));
        result = ((result* 31)+((this.noPersist == null)? 0 :this.noPersist.hashCode()));
        result = ((result* 31)+((this.noLastConfig == null)? 0 :this.noLastConfig.hashCode()));
        result = ((result* 31)+((this.noLog == null)? 0 :this.noLog.hashCode()));
        result = ((result* 31)+((this.noHardware == null)? 0 :this.noHardware.hashCode()));
        result = ((result* 31)+((this.messageTrace == null)? 0 :this.messageTrace.hashCode()));
        result = ((result* 31)+((this.softwareFirmwareValue == null)? 0 :this.softwareFirmwareValue.hashCode()));
        result = ((result* 31)+((this.noWriteback == null)? 0 :this.noWriteback.hashCode()));
        result = ((result* 31)+((this.msTimestamp == null)? 0 :this.msTimestamp.hashCode()));
        result = ((result* 31)+((this.noLastStart == null)? 0 :this.noLastStart.hashCode()));
        result = ((result* 31)+((this.badLevel == null)? 0 :this.badLevel.hashCode()));
        result = ((result* 31)+((this.tweakState == null)? 0 :this.tweakState.hashCode()));
        result = ((result* 31)+((this.spamState == null)? 0 :this.spamState.hashCode()));
        result = ((result* 31)+((this.noState == null)? 0 :this.noState.hashCode()));
        result = ((result* 31)+((this.badState == null)? 0 :this.badState.hashCode()));
        result = ((result* 31)+((this.baseState == null)? 0 :this.baseState.hashCode()));
        result = ((result* 31)+((this.noStatus == null)? 0 :this.noStatus.hashCode()));
        result = ((result* 31)+((this.fastWrite == null)? 0 :this.fastWrite.hashCode()));
        result = ((result* 31)+((this.noFolder == null)? 0 :this.noFolder.hashCode()));
        result = ((result* 31)+((this.noProxy == null)? 0 :this.noProxy.hashCode()));
        result = ((result* 31)+((this.missingPoint == null)? 0 :this.missingPoint.hashCode()));
        result = ((result* 31)+((this.badCategory == null)? 0 :this.badCategory.hashCode()));
        result = ((result* 31)+((this.extraPoint == null)? 0 :this.extraPoint.hashCode()));
        result = ((result* 31)+((this.badAddr == null)? 0 :this.badAddr.hashCode()));
        result = ((result* 31)+((this.smokeCheck == null)? 0 :this.smokeCheck.hashCode()));
        result = ((result* 31)+((this.redirectRegistry == null)? 0 :this.redirectRegistry.hashCode()));
        result = ((result* 31)+((this.noPointState == null)? 0 :this.noPointState.hashCode()));
        result = ((result* 31)+((this.disableWriteback == null)? 0 :this.disableWriteback.hashCode()));
        result = ((result* 31)+((this.barfConfig == null)? 0 :this.barfConfig.hashCode()));
        result = ((result* 31)+((this.extraField == null)? 0 :this.extraField.hashCode()));
        result = ((result* 31)+((this.emptyMissing == null)? 0 :this.emptyMissing.hashCode()));
        result = ((result* 31)+((this.fixedSampleRate == null)? 0 :this.fixedSampleRate.hashCode()));
        result = ((result* 31)+((this.dupeState == null)? 0 :this.dupeState.hashCode()));
        result = ((result* 31)+((this.featureEnableSwap == null)? 0 :this.featureEnableSwap.hashCode()));
        result = ((result* 31)+((this.delayWrite == null)? 0 :this.delayWrite.hashCode()));
        result = ((result* 31)+((this.extraDevice == null)? 0 :this.extraDevice.hashCode()));
        result = ((result* 31)+((this.noConfigAck == null)? 0 :this.noConfigAck.hashCode()));
        result = ((result* 31)+((this.badVersion == null)? 0 :this.badVersion.hashCode()));
        result = ((result* 31)+((this.fixedLogLevel == null)? 0 :this.fixedLogLevel.hashCode()));
        result = ((result* 31)+((this.configStateDelay == null)? 0 :this.configStateDelay.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PubberOptions) == false) {
            return false;
        }
        PubberOptions rhs = ((PubberOptions) other);
        return (((((((((((((((((((((((((((((((((((((((((this.skewClock == rhs.skewClock)||((this.skewClock!= null)&&this.skewClock.equals(rhs.skewClock)))&&((this.noPersist == rhs.noPersist)||((this.noPersist!= null)&&this.noPersist.equals(rhs.noPersist))))&&((this.noLastConfig == rhs.noLastConfig)||((this.noLastConfig!= null)&&this.noLastConfig.equals(rhs.noLastConfig))))&&((this.noLog == rhs.noLog)||((this.noLog!= null)&&this.noLog.equals(rhs.noLog))))&&((this.noHardware == rhs.noHardware)||((this.noHardware!= null)&&this.noHardware.equals(rhs.noHardware))))&&((this.messageTrace == rhs.messageTrace)||((this.messageTrace!= null)&&this.messageTrace.equals(rhs.messageTrace))))&&((this.softwareFirmwareValue == rhs.softwareFirmwareValue)||((this.softwareFirmwareValue!= null)&&this.softwareFirmwareValue.equals(rhs.softwareFirmwareValue))))&&((this.noWriteback == rhs.noWriteback)||((this.noWriteback!= null)&&this.noWriteback.equals(rhs.noWriteback))))&&((this.msTimestamp == rhs.msTimestamp)||((this.msTimestamp!= null)&&this.msTimestamp.equals(rhs.msTimestamp))))&&((this.noLastStart == rhs.noLastStart)||((this.noLastStart!= null)&&this.noLastStart.equals(rhs.noLastStart))))&&((this.badLevel == rhs.badLevel)||((this.badLevel!= null)&&this.badLevel.equals(rhs.badLevel))))&&((this.tweakState == rhs.tweakState)||((this.tweakState!= null)&&this.tweakState.equals(rhs.tweakState))))&&((this.spamState == rhs.spamState)||((this.spamState!= null)&&this.spamState.equals(rhs.spamState))))&&((this.noState == rhs.noState)||((this.noState!= null)&&this.noState.equals(rhs.noState))))&&((this.badState == rhs.badState)||((this.badState!= null)&&this.badState.equals(rhs.badState))))&&((this.baseState == rhs.baseState)||((this.baseState!= null)&&this.baseState.equals(rhs.baseState))))&&((this.noStatus == rhs.noStatus)||((this.noStatus!= null)&&this.noStatus.equals(rhs.noStatus))))&&((this.fastWrite == rhs.fastWrite)||((this.fastWrite!= null)&&this.fastWrite.equals(rhs.fastWrite))))&&((this.noFolder == rhs.noFolder)||((this.noFolder!= null)&&this.noFolder.equals(rhs.noFolder))))&&((this.noProxy == rhs.noProxy)||((this.noProxy!= null)&&this.noProxy.equals(rhs.noProxy))))&&((this.missingPoint == rhs.missingPoint)||((this.missingPoint!= null)&&this.missingPoint.equals(rhs.missingPoint))))&&((this.badCategory == rhs.badCategory)||((this.badCategory!= null)&&this.badCategory.equals(rhs.badCategory))))&&((this.extraPoint == rhs.extraPoint)||((this.extraPoint!= null)&&this.extraPoint.equals(rhs.extraPoint))))&&((this.badAddr == rhs.badAddr)||((this.badAddr!= null)&&this.badAddr.equals(rhs.badAddr))))&&((this.smokeCheck == rhs.smokeCheck)||((this.smokeCheck!= null)&&this.smokeCheck.equals(rhs.smokeCheck))))&&((this.redirectRegistry == rhs.redirectRegistry)||((this.redirectRegistry!= null)&&this.redirectRegistry.equals(rhs.redirectRegistry))))&&((this.noPointState == rhs.noPointState)||((this.noPointState!= null)&&this.noPointState.equals(rhs.noPointState))))&&((this.disableWriteback == rhs.disableWriteback)||((this.disableWriteback!= null)&&this.disableWriteback.equals(rhs.disableWriteback))))&&((this.barfConfig == rhs.barfConfig)||((this.barfConfig!= null)&&this.barfConfig.equals(rhs.barfConfig))))&&((this.extraField == rhs.extraField)||((this.extraField!= null)&&this.extraField.equals(rhs.extraField))))&&((this.emptyMissing == rhs.emptyMissing)||((this.emptyMissing!= null)&&this.emptyMissing.equals(rhs.emptyMissing))))&&((this.fixedSampleRate == rhs.fixedSampleRate)||((this.fixedSampleRate!= null)&&this.fixedSampleRate.equals(rhs.fixedSampleRate))))&&((this.dupeState == rhs.dupeState)||((this.dupeState!= null)&&this.dupeState.equals(rhs.dupeState))))&&((this.featureEnableSwap == rhs.featureEnableSwap)||((this.featureEnableSwap!= null)&&this.featureEnableSwap.equals(rhs.featureEnableSwap))))&&((this.delayWrite == rhs.delayWrite)||((this.delayWrite!= null)&&this.delayWrite.equals(rhs.delayWrite))))&&((this.extraDevice == rhs.extraDevice)||((this.extraDevice!= null)&&this.extraDevice.equals(rhs.extraDevice))))&&((this.noConfigAck == rhs.noConfigAck)||((this.noConfigAck!= null)&&this.noConfigAck.equals(rhs.noConfigAck))))&&((this.badVersion == rhs.badVersion)||((this.badVersion!= null)&&this.badVersion.equals(rhs.badVersion))))&&((this.fixedLogLevel == rhs.fixedLogLevel)||((this.fixedLogLevel!= null)&&this.fixedLogLevel.equals(rhs.fixedLogLevel))))&&((this.configStateDelay == rhs.configStateDelay)||((this.configStateDelay!= null)&&this.configStateDelay.equals(rhs.configStateDelay))));
    }

}
