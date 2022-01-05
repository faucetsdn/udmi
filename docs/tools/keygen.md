[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./)
/ [Keygen](#)

# Auth Key Generator

The `bin/keygen` script will generate an RSA or ES key for a single device. The `bin/genkeys` script
generates keys for all devices listed in a site directory.

If will only generate keys for devices that are missing a public key, and also ones that have a
`.cloud.auth_type` parameter configured in their `metadata.json` file.

```
~/udmi$ rm test_site/devices/AHU-1/rsa_public.pem
~/udmi$ bin/genkeys test_site/
test_site/devices/GAT-123/rsa_public.pem exists.
test_site/devices/AHU-22/metadata.json missing .cloud.auth_type
test_site/devices/SNS-4/metadata.json missing .cloud.auth_type
ls: cannot access 'test_site/devices/AHU-1/*_public.pem': No such file or directory
Generating RSA private key, 2048 bit long modulus (2 primes)
........................................................................+++++
...................................................+++++
e is 65537 (0x010001)
writing RSA key
```
