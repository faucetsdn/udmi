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

If properly enabled, the _nonce_ will show up in the sequencer output, in this case for the specific _system_
config sub-folder:
```
 2022-10-05T21:30:58Z TRACE sequencer received config_system_2022-10-05T21:30:58.072Z:
 {
   "extra_field" : "reset_config",
   "metrics_rate_sec" : 600,
   "min_loglevel" : 200,
   "nonce" : 1665005451790,
   "testing" : {
     "sequence_name" : "reset_config"
   },
   "timestamp" : "2022-10-05T21:30:58.072Z",
   "version" : "1.3.14-85-g61f475b7"
 }
 ```
 
And then should immediately be followed by a "promoted" update, where the _system_ update is incorporated
into the complete _config_ update:
```
 2022-10-05T21:30:58Z TRACE sequencer received config_update_2022-10-05T21:30:58.133Z:
 {
   "nonce" : 1665005451790,
   "system" : {
     "min_loglevel" : 200,
     "metrics_rate_sec" : 600,
     "testing" : {
       "sequence_name" : "reset_config"
     },
     "extra_field" : "reset_config",
     "nonce" : 1665005451790
   },
   "timestamp" : "2022-10-05T21:30:58.133Z",
   "version" : "1.3.14-85-g61f475b7"
 }
```

## Cloud Functions

The _config_ updates themselves are processed by some cloud functions. The _udmi__reflect_ function receives the sequencer
messags, and the _udmi__config_ function handles the config update itself. The logs of these can be searched for the specific
_nonce_ to show a more detailed accounting of what is going on:
```
~/udmi$ gcloud --project=$project_id functions logs read udmi_reflect --sort-by=time_utc --limit=1000 | fgrep 1665005451790
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.755  Reflect ZZ-TRI-FECTA AHU-1 config system 1665005451790
```

From that, you can look up the complete function execution context run:
```
~/udmi$ gcloud --project=$project_id functions logs read udmi_reflect --sort-by=time_utc --limit=1000 | fgrep igx30b2gw5gg
D        udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:54.043  Function execution started
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:54.650  Setting GCLOUD_PROJECT to bos-testing-ci
WARNING  udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:54.748  Warning, estimating Firebase Config based on GCLOUD_PROJECT. Initializing firebase-admin may fail
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.518  No FIREBASE_CONFIG defined
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.518  Using UDMI version 1.3.14-85-g61f475b7
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.634  Fetching registries for us-central1
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.636  Fetching registries for europe-west1
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.637  Fetching registries for asia-east1
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.755  Reflect ZZ-TRI-FECTA AHU-1 config system 1665005451790
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:55.887  Processing results for us-central1
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:56.399  Processing results for europe-west1
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:56.668  Message publish udmi_config {"deviceId":"AHU-1","deviceNumId":"2612218335398339","deviceRegistryId":"ZZ-TRI-FECTA","deviceRegistryLocation":"us-central1","projectId":"bos-testing-ci","subFolder":"system","subType":"config","cloudRegion":"us-central1"}
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:56.668  Fetched 2 registry regions
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:56.668  Processing results for asia-east1
         udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:56.999  Message 5834149510944859 published to udmi_config.
D        udmi_reflect  igx30b2gw5gg  2022-10-05 21:30:57.004  Function execution took 2960 ms. Finished with status: ok
```

The same logic can be applied to the _udmi__config_ function, but there's a lot more entries in those logs, so you may need to add a specific _end-time_ filter:
```
~/udmi$ gcloud --project=$project_id functions logs read udmi_config --sort-by=time_utc --limit=1000 --end-time=2022-10-05T21:31:57.004 | fgrep 1665005451790
         udmi_config  u1az18rg4bh8  2022-10-05 21:30:58.072  Config message ZZ-TRI-FECTA AHU-1 system 1665005451790 {"min_loglevel":200,"metrics_rate_sec":600,"testing":{"sequence_name":"reset_config"},"extra_field":"reset_config","nonce":1665005451790}
         udmi_config  u1az18rg4bh8  2022-10-05 21:30:58.073  command devices/AHU-1/config/system 1665005451790 projects/bos-testing-ci/locations/us-central1/registries/UDMS-REFLECT/devices/ZZ-TRI-FECTA
         udmi_config  u1az18rg4bh8  2022-10-05 21:30:58.133  Config modify system 86490 2022-10-05T21:30:58.073Z 1665005451790
         udmi_config  u1az18rg4bh8  2022-10-05 21:30:58.189  command devices/AHU-1/config/update 1665005451790 projects/bos-testing-ci/locations/us-central1/registries/UDMS-REFLECT/devices/ZZ-TRI-FECTA
         udmi_config  u1az18rg4bh8  2022-10-05 21:30:58.218  Config accepted system 86490 2022-10-05T21:30:58.073Z 1665005451790
```

## subFolder Updates

For config blocks that have multiple sub-folders, there will be a _nonce_ for each and also at the top
level. The top-level one indicates which subfolder was added most recently. Although nominally a timestamp,
the numerical ordering of the _nonce_ is not reliable as a source of information.
```
 2022-10-05T21:31:03Z TRACE sequencer received config_update_2022-10-05T21:31:02.950Z:
 {
   "nonce" : 1665005461272,
   "pointset" : {
     "points" : {
       "filter_alarm_pressure_status" : {
         "ref" : "BV11.present_value"
       },
       "filter_differential_pressure_setpoint" : {
         "set_value" : 98
       },
       "filter_differential_pressure_sensor" : {
         "ref" : "AV12.present_value"
       }
     },
     "nonce" : 1665005461272
   },
   "system" : {
     "min_loglevel" : 200,
     "metrics_rate_sec" : 600,
     "testing" : {
       "sequence_name" : "writeback_states"
     },
     "nonce" : 1665005459267,
     "last_start" : "2022-10-05T21:30:45Z"
   },
   "timestamp" : "2022-10-05T21:31:02.950Z",
   "version" : "1.3.14-85-g61f475b7"
 }
```
