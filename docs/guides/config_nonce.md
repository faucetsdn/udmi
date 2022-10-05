[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Config Nonce](#)

# Config Nonce

The _config nonce_ is a debugging feature that can be used to track down problems with configuration updates
in the context of UDMI cloud services. It's essentially a unique token that's inserted into config updates
(from the _sequencer_) that are tracked through the system.

## Enabling Trace Debug

By adding the `-vv` flag to _sequencer_, the system will output more stuff and also include a special `nonce`
parameter to config messages and updates.

For GitHub Actions CI testing, setting the `SEQUENCER_OPTS` secret to `-vv` will automatically enable it there.

## Sequencer Output

If properly enabled, the _nonce_ will show up in the sequencer output:
```
2022-10-05T13:53:32Z TRACE sequencer received config_update_2022-10-05T13:53:29.980Z:
{
   "nonce" : 1664978008701,
   "system" : {
     "min_loglevel" : 200,
     "metrics_rate_sec" : 600,
     "testing" : {
       "sequence_name" : "writeback_states"
     },
     "nonce" : 1664978008701,
     "last_start" : "2022-10-05T13:53:17Z"
   },
   "timestamp" : "2022-10-05T13:53:29.980Z",
   "version" : "1.3.14-85-g61f475b7"
 }
```

The `nonce` field will show up at various levels of the config hierarchy. The one under _system_ indicates
the value generated when the update was created. The one at the top level indicates the one used when the
folder update was combined into the complete structure (see below for more details on this).

## Cloud Functions

peringknife@peringknife-glaptop4:~/udmi$ gcloud --project=$project_id functions logs read udmi_reflect --sort-by=time_utc --limit=1000 | fgrep 1664978010705
         udmi_reflect  nv4qq2uyyqff  2022-10-05 13:53:30.820  Reflect ZZ-TRI-FECTA AHU-1 config pointset 1664978010705

## subFolder Updates

2022-10-05T13:53:34.2067262Z 2022-10-05T13:53:34Z TRACE sequencer received config_pointset_2022-10-05T13:53:33.096Z:
2022-10-05T13:53:34.2067578Z {
2022-10-05T13:53:34.2067777Z   "nonce" : 1664978010705,
2022-10-05T13:53:34.2068296Z   "points" : {
2022-10-05T13:53:34.2068547Z     "filter_alarm_pressure_status" : {
2022-10-05T13:53:34.2068811Z       "ref" : "BV11.present_value"
2022-10-05T13:53:34.2069027Z     },
2022-10-05T13:53:34.2069271Z     "filter_differential_pressure_setpoint" : {
2022-10-05T13:53:34.2069532Z       "set_value" : 98
2022-10-05T13:53:34.2069727Z     },
2022-10-05T13:53:34.2069957Z     "filter_differential_pressure_sensor" : {
2022-10-05T13:53:34.2070225Z       "ref" : "AV12.present_value"
2022-10-05T13:53:34.2070428Z     }
2022-10-05T13:53:34.2070598Z   },
2022-10-05T13:53:34.2070896Z   "timestamp" : "2022-10-05T13:53:33.096Z",
2022-10-05T13:53:34.2071197Z   "version" : "1.3.14-85-g61f475b7"
2022-10-05T13:53:34.2071395Z }
2022-10-05T13:53:34.2076341Z +- Remove `pointset`
2022-10-05T13:53:34.3162410Z 2022-10-05T13:53:34Z TRACE sequencer received config_update_2022-10-05T13:53:34.213Z:
2022-10-05T13:53:34.3163098Z {
2022-10-05T13:53:34.3163414Z   "nonce" : 1664978010705,
2022-10-05T13:53:34.3164076Z   "pointset" : {
2022-10-05T13:53:34.3164339Z     "points" : {
2022-10-05T13:53:34.3164589Z       "filter_alarm_pressure_status" : {
2022-10-05T13:53:34.3164862Z         "ref" : "BV11.present_value"
2022-10-05T13:53:34.3165073Z       },
2022-10-05T13:53:34.3165323Z       "filter_differential_pressure_setpoint" : {
2022-10-05T13:53:34.3165581Z         "set_value" : 98
2022-10-05T13:53:34.3165784Z       },
2022-10-05T13:53:34.3166026Z       "filter_differential_pressure_sensor" : {
2022-10-05T13:53:34.3166295Z         "ref" : "AV12.present_value"
2022-10-05T13:53:34.3166506Z       }
2022-10-05T13:53:34.3166685Z     },
2022-10-05T13:53:34.3166876Z     "nonce" : 1664978010705
2022-10-05T13:53:34.3167074Z   },
2022-10-05T13:53:34.3167247Z   "system" : {
2022-10-05T13:53:34.3167453Z     "min_loglevel" : 200,
2022-10-05T13:53:34.3167676Z     "metrics_rate_sec" : 600,
2022-10-05T13:53:34.3167889Z     "testing" : {
2022-10-05T13:53:34.3168126Z       "sequence_name" : "writeback_states"
2022-10-05T13:53:34.3168339Z     },
2022-10-05T13:53:34.3168529Z     "nonce" : 1664978008701,
2022-10-05T13:53:34.3168955Z     "last_start" : "2022-10-05T13:53:17Z"
2022-10-05T13:53:34.3169187Z   },
2022-10-05T13:53:34.3169469Z   "timestamp" : "2022-10-05T13:53:34.213Z",
2022-10-05T13:53:34.3169793Z   "version" : "1.3.14-85-g61f475b7"
2022-10-05T13:53:34.3169992Z }
