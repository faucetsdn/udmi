[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Reset Config](#)

# Send a configuration message to a specific device

The `reset_config` tool can be used to send a configuration message to a specific device.

The `reset_config` command can be invoked from the `udmi` folder in the following way:

```
bin/reset_config SITE_DIR PROJECT_ID DEVICE_ID [CONFIG_FILE]
```

For example, if the UDMI site model is in the `path/to/site_model` folder, if the GCP project ID is 
`my_project` and the device id is `TEST-1`, the command line will look like this:

```
bin/reset_config path/to/site_model my_project TEST-1
```

In this case, the `reset_config` tool will use the `generated_config.json` file that the `registrar` 
tool has created in the `devices/TEST-1/out` folder.

It is also possible to send a specific `config` payload.

In this case, it is necessary to store a file including the `config` payload in the `devices/DEVICE_ID/config` folder.

For example, if a file named `config.json` has been created for this purpose in the `devices/TEST-1/config` folder,
it will be possible to invoke the `reset_config` tool with the following command:

```
bin/reset_config path/to/site_model my_project TEST-1 config.json
```

Finally, it is possible to get `registrar` to send a user defined config instead of a generated config file.

For this purpose, edit the `metadata.json` file to include the following `cloud` configuration object:

```
  "cloud": {
    "config": {
      "static_file": "config.json"
    }
  }
```

and store the `config.json` file in the `devices/DEVICE_ID/config` folder.
