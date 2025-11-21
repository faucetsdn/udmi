### Sample udmis service output for bridgehead docker setup 

The udmis service first waits 15 seconds for the mosquitto container to setup, and then takes under 60 seconds to set itself up. You'll know the service is up and running when you see the line: `udmis running in the background, pid XX log in /tmp/udmis.log`. Look for that exact line and no big errors above it (The process ID, or PID, shown as XX, will be a different number every time you run the service). Below shows the command you should run to see your log, as well as a sample successful udmis log output.

You should run the following command in the same directory (`/bridgehead`) where you ran the initial docker compose command: `sudo docker logs udmis -f`

Once you have confirmed the udmis service is running, press `Ctrl` + `C` on your keyboard to exit the log view and return to your terminal prompt.

Sample output:
```
~ # cat /usr/local/bin/startup_output.txt 
Waiting 15s for mqtt to setup
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
fatal: not a git repository (or any of the parent directories): .git
Starting local services at 2025-11-05T09:24:26+00:00
Starting udmis proper...
Waiting for udmis startup 29...
Waiting for udmis startup 28...
Waiting for udmis startup 27...
Waiting for udmis startup 26...
Waiting for udmis startup 25...
::::::::: tail /tmp/udmis.log
2025-11-05T09:24:31.461Z xxxxxxxx N: MessageDispatcherImpl Scheduling provision execution, after 60s every 60s
2025-11-05T09:24:31.462Z xxxxxxxx I: MessageDispatcherImpl dispatcher/provision activating provision with 3e7dfd63
2025-11-05T09:24:31.462Z xxxxxxxx D: SimpleMqttPipe Activating message pipe provision as 0000001f => 31
2025-11-05T09:24:31.462Z xxxxxxxx N: SimpleMqttPipe Creating new source queue provision with capacity 1000
2025-11-05T09:24:31.462Z xxxxxxxx D: SimpleMqttPipe Handling provision
2025-11-05T09:24:31.462Z xxxxxxxx I: SimpleMqttPipe Starting message loop 723ed5a0:00
2025-11-05T09:24:31.462Z xxxxxxxx I: SimpleMqttPipe Starting message loop 723ed5a0:02
2025-11-05T09:24:31.462Z xxxxxxxx I: SimpleMqttPipe Starting message loop 723ed5a0:01
2025-11-05T09:24:31.463Z xxxxxxxx I: SimpleMqttPipe Starting message loop 723ed5a0:03
2025-11-05T09:24:31.463Z xxxxxxxx I: SimpleMqttPipe Subscribed mqtt-295ac31c to topic /r/+/d/+/events/discovery
2025-11-05T09:24:31.463Z xxxxxxxx I: StateProcessor Activating
2025-11-05T09:24:31.463Z xxxxxxxx N: StateProcessor Scheduling state execution, after 60s every 60s
2025-11-05T09:24:31.463Z xxxxxxxx D: MessageDispatcherImpl Registering handler for Object in dispatcher/state
2025-11-05T09:24:31.463Z xxxxxxxx D: MessageDispatcherImpl Registering handler for Exception in dispatcher/state
2025-11-05T09:24:31.464Z xxxxxxxx D: MessageDispatcherImpl Registering handler for StateUpdate in dispatcher/state
2025-11-05T09:24:31.464Z xxxxxxxx I: MessageDispatcherImpl Activating
2025-11-05T09:24:31.464Z xxxxxxxx N: MessageDispatcherImpl Scheduling state execution, after 60s every 60s
2025-11-05T09:24:31.464Z xxxxxxxx I: MessageDispatcherImpl dispatcher/state activating state with 3d36e013
2025-11-05T09:24:31.464Z xxxxxxxx D: SimpleMqttPipe Activating message pipe state as 0000001f => 31
2025-11-05T09:24:31.464Z xxxxxxxx N: SimpleMqttPipe Creating new source queue state with capacity 1000
2025-11-05T09:24:31.464Z xxxxxxxx D: SimpleMqttPipe Handling state
2025-11-05T09:24:31.464Z xxxxxxxx I: SimpleMqttPipe Starting message loop 7abe27de:00
2025-11-05T09:24:31.465Z xxxxxxxx I: SimpleMqttPipe Starting message loop 7abe27de:01
2025-11-05T09:24:31.465Z xxxxxxxx I: SimpleMqttPipe Starting message loop 7abe27de:02
2025-11-05T09:24:31.465Z xxxxxxxx I: SimpleMqttPipe Starting message loop 7abe27de:03
2025-11-05T09:24:31.465Z xxxxxxxx I: SimpleMqttPipe Subscribed mqtt-89bd90c3 to topic /r/+/d/+/state
2025-11-05T09:24:31.465Z xxxxxxxx I: PubSubIotAccessProvider Activating
2025-11-05T09:24:31.466Z xxxxxxxx I: PubSubIotAccessProvider Fetching registries for: 
2025-11-05T09:24:31.466Z xxxxxxxx D: PubSubIotAccessProvider Fetched 0 registry regions
2025-11-05T09:24:31.466Z xxxxxxxx N: UdmiServicePod Finished activation of container components, created /tmp/pod_ready.txt
udmis running in the background, pid 39 log in /tmp/udmis.log
Blocking until termination.
```