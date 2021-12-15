# Status Objects

The State and system/logentry messages both have `status` and `entries` sub-fields, respectivly, that
follow the same structure.
* State `status` entries represent 'sticky' conditions that persist until the situation is cleared,
e.g. "device disconnected".
* A `statuses` entry is a map of 'sticky' conditions that are keyed on a value that can be
used to manage updates by a particular (device dependent) subsystem.
* Logentry `entries` fields are transitory event that happen, e.g. "connection failed".
* The log `entries` field is an array that can be used to collaesce multiple log updates into
one message.
* Config parse errors should be represented as a system-level device state `status` entry.
* The `message` field sould be a one-line representation of the triggering condition.
* The `detail` field can be multi-line and include more detail, e.g. a complete program
stack-trace.
* The `category` field is a device-specific representation of which sub-system the message comes
from. In a Java environment, for example, it would be the fully qualified path name of the Class
triggering the message.
* A `status` or `statuses` `timestamp` field should be the timestamp the condition was triggered,
or most recently updated. It might be different than the top-level message `timestamp` if the
condition is not checked often or is sticky until it's cleared.
* A logentry `entries` `timestamp` field is the time that the event occured, which is potentially
different than the top-level `timestamp` field (which is when the log was sent).
* The status `level` should conform to the numerical
[Stackdriver LogEntry](https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity)
levels. The `DEFAULT` value of 0 is not allowed (lowest value is 100, maximum 800).
