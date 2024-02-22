# Registrar validation

A tool which can be used to validate registrar, and test cloud registrar.

Works by connecting:
- Connecting each device which has a private key to clearblade
- Subscribing to the config, and validating that it matches the generated_config for the device
- If `gateway.proxy_ids` is defined:
    - Attach to each proxy device
    - Subscribe to the config for each proxy device
    - Validate that it matches the generated_config for each proxy device

## Usage

```
./test.sh PATH_TO_SITE_MODEL PROJECT_ID
```