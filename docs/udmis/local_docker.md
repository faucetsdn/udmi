[**UDMI**](../../) / [**Docs**](../) / [**UDMIS**](.) / [Local Docker](#)

# Local docker UDMIS setup and execution

This document describes how to setup and run all the UDMI components locally using docker, so no
specific install required. All the commands here should be direct cut-and-paste into a suitable
shell window.

_Note:_ This is still in 'alpha' form. It works, but there's likely going to be some kinks to work out
(likely to do with authentication, because authentication is annoying).

## Configuration Setup

These setup variables can be customized for any particular setup. It's recommended to first
get everything working with the built-in _pubber_ as shown, and then try with a real device after!
```
site_model=udmi_site_model
device_id=AHU-1
serial_no=8127324
```

## Environment Setup

One-time setup pieces to setup docker and a test site model, if necessary.
```
docker inspect -f ok udminet || docker network create udminet --subnet 192.168.99.0/24
[[ ${site_model} != udmi_site_model ]] || git clone https://github.com/faucetsdn/udmi_site_model.git
```

## UDMIS Container Startup

Starts the background UDMIS locally. The output of this command is uninteresting (assuming
it works), so see the next section for some sample diagnostic commands. It will take some time
(less than 30s) to get up and going before the other commands will work.
```
docker run -d --rm --net udminet --name udmis -p 8883:8883 \
    -v $(realpath $site_model):/root/site \
    -v $PWD/var/tmp:/tmp \
    -v $PWD/var/etcd:/root/udmi/default.etcd \
    -v $PWD/var/mosquitto:/etc/mosquitto \
    ghcr.io/faucetsdn/udmi:udmis-latest udmi/bin/start_local block site/ //mqtt/udmis
```

## UDMIS Startup Diagnostics

Some sample commands to inspect and diagnose what's going on with the UDMIS startup. See
the [sample UDMIS output](udmis_output.md) for what this might look like. Real diagnosing
will require a bit more investigation than shown here, but if you see the same output
then you can assume that the system is working!
```
docker logs udmis 2>&1 | fgrep udmis
fgrep pod_ready var/tmp/udmis.log
ls -l var/tmp/pod_ready.txt
```

## Registrar Run

After startup, the site model needs to be registered as per standard UDMI practice. This only
needs to be done once per site model (or after any significant changes). The
[sample registrar output](registrar_output.md) shows what a successful run looks like.
```
docker run --rm --net udminet --name registrar -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/registrar site/ //mqtt/udmis
```

## Pubber Instance

For initial install testing, it's recommended to try first with the standard _pubber_ utility.
See the [sample pubber output](pubber_output.md) for the beginning of what this run looks like.

```
docker run -d --rm --net udminet --name pubber -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:pubber-latest bin/pubber site/ //mqtt/udmis ${device_id} ${serial_no}
```

## Sequencer Testing

Sequencer can be run directly as per normal too. See the [sample sequencer output](sequencer_output.md)
for what the beginning of a successful run looks like.
```
docker run --rm --net udminet --name sequencer -v $(realpath $site_model):/root/site \
    ghcr.io/faucetsdn/udmi:validator-latest bin/sequencer site/ //mqtt/udmis ${device_id} ${serial_no}
```

The resulting output can be extracted from the site model, See the [sample report output](report_output.md)
for what this should look like.
```
head ${site_model}/out/devices/${device_id}/results.md 
```

# Container Build

For development purposes, the following command will build (and push) the requisite docker images. This
requires a full UDMI install and appropriate permissions (to push).
```
for image in udmis validator pubber; do
  docker rmi ghcr.io/faucetsdn/udmi:$image-latest; \
  bin/container $image push \
  && docker tag $image:latest ghcr.io/faucetsdn/udmi:$image-latest \
  && docker push ghcr.io/faucetsdn/udmi:$image-latest
done
```
