
DNS_ZONE="@GCP_PROJECT_ID@" # defined in terraform

IP1_NAME=haproxyip1
HOSTNAME1=@HOSTNAME_1@
CERT1=haproxycerta

IP2_NAME=haproxyip2
HOSTNAME2=@HOSTNAME_1@
CERT2=haproxycertb

SERVICE_NAME=haproxy-multi
ZONE=us-central1-f
REGION=us-central1

SSL_POLICY_NAME=haproxy-ssl-policy

#Forwarding rule --> Front end --> backend service --> backend --> node -->
#create_proxy 1 IPADDRESS PORT CERTNAME BACKEND NODEPORT ZONE
#             1     2       3       
function create_proxy(){
    suffix=$1
    ip_address=$2
    port=$3
    node_port=$6

    CERT_NAME=$4
    instance_group=$5
    region=$7

    LB_BACKEND_SERVICE="haproxy-lb-backend-${suffix}"
    HEALTHCHECK="haproxy-healthcheck-${suffix}"
    NODE_NAMED_PORT="port${node_port}"
    HEALTHCHECK="haproxy-backend-healthcheck-${suffix}"
    FORWARDING_RULE="haproxy-lb-${suffix}"
    FRONTEND="haproxy-lb-frontend-${suffix}"
    FIREWALL_RULE="haproxy-gfe-backend-${suffix}"

    HEALTHCHECK_PORT=10256

    gcloud compute health-checks create http $HEALTHCHECK \
        --check-interval=5 \
        --healthy-threshold=2 \
        --host="" \
        --request-path="/healthz" \
        --timeout=5s \
        --unhealthy-threshold=2 \
        --global \
        --port=$HEALTHCHECK_PORT

    gcloud compute backend-services create $LB_BACKEND_SERVICE \
        --global-health-checks \
        --protocol=TCP \
        --port-name=$NODE_NAMED_PORT \
        --health-checks=$HEALTHCHECK \
        --timeout=20m \
        --global

    gcloud compute backend-services add-backend $LB_BACKEND_SERVICE \
        --instance-group=$instance_group \
        --instance-group-zone=$region\
        --balancing-mode=CONNECTION \
        --max-connections=100000000 \
        --capacity-scaler=1 \
        --global 

    gcloud compute target-ssl-proxies create $FRONTEND \
        --backend-service=$LB_BACKEND_SERVICE \
        --ssl-certificates=$CERT_NAME \
        --ssl-policy=$SSL_POLICY_NAME \
        --proxy-header=PROXY_V1

    gcloud compute forwarding-rules create $FORWARDING_RULE \
        --global \
        --target-ssl-proxy=$FRONTEND \
        --address=$ip_address \
        --ports=$port

    gcloud compute firewall-rules create $FIREWALL_RULE \
        --source-ranges=130.211.0.0/22,35.191.0.0/16 \
        --target-tags=$network_tag \
        --allow=tcp:$node_port

}


healthcheck=$(kubectl get service $SERVICE_NAME -o jsonpath="{.metadata.annotations['service\.kubernetes\.io/healthcheck']}")
firewall_rule=$(kubectl get service $SERVICE_NAME -o jsonpath="{.metadata.annotations['service\.kubernetes\.io/firewall-rule']}")
firewall_rule_for_hc=$(kubectl get service $SERVICE_NAME -o jsonpath="{.metadata.annotations['service\.kubernetes\.io/firewall-rule-for-hc']}")
tcp_forwarding_rule=$(kubectl get service $SERVICE_NAME -o jsonpath="{.metadata.annotations['service\.kubernetes\.io/firewall-rule']}")
K8_BACKEND_SERVICE=$(kubectl get service $SERVICE_NAME -o jsonpath="{.metadata.annotations['service\.kubernetes\.io/backend-service']}")      

node_port1=$(kubectl get service $SERVICE_NAME -o jsonpath="{.spec.ports[?(@.name == 'tcp-port1')].nodePort}")
node_port2=$(kubectl get service $SERVICE_NAME -o jsonpath="{.spec.ports[?(@.name == 'tcp-port2')].nodePort}")
node_port3=$(kubectl get service $SERVICE_NAME -o jsonpath="{.spec.ports[?(@.name == 'tcp-port3')].nodePort}")
node_port4=$(kubectl get service $SERVICE_NAME -o jsonpath="{.spec.ports[?(@.name == 'tcp-port4')].nodePort}")

network_tag=$(gcloud compute firewall-rules describe $firewall_rule | yq e ".targetTags[0]")
# Get the network tag for the gke managed firewall rules and use that

echo $firewall_rule
echo $K8_BACKEND_SERVICE
echo $node_port1
echo $node_port2
echo $node_port3

K8_INSTANCE_GROUP=$(gcloud compute backend-services describe --region=us-central1 k8s2-g28k9pv9-default-haproxy-multi-vusraaod | yq e ".backends[0].group" | sed 's/.*\///')

IP1_ADDR=$(gcloud compute addresses describe $IP1_NAME --global | ggrep -Po "address: \K(.*)$")
IP2_ADDR=$(gcloud compute addresses describe $IP2_NAME --global | ggrep -Po "address: \K(.*)$")

# Housework
########

# Create IP AddressS

function setup_domain_ip_and_certs(){
    gcloud compute addresses create $IP1_NAME --global
    IP1_ADDR=$(gcloud compute addresses describe $IP1_NAME --global | ggrep -Po "address: \K(.*)$")
    echo created $IP1_ADDR

    gcloud compute addresses create $IP2_NAME --global
    IP2_ADDR=$(gcloud compute addresses describe $IP2_NAME --global | ggrep -Po "address: \K(.*)$")
    echo created $IP2_ADDR

    # Assign domain to IP address
    gcloud dns record-sets transaction abort --zone=$DNS_ZONE
    gcloud dns record-sets transaction start --zone=$DNS_ZONE
    gcloud dns record-sets transaction add $IP1_ADDR \
        --name=$HOSTNAME1 \
        --ttl=300 \
        --type=A \
        --zone=$DNS_ZONE
    gcloud dns record-sets transaction add $IP2_ADDR \
        --name=$HOSTNAME2 \
        --ttl=300 \
        --type=A \
        --zone=$DNS_ZONE
    gcloud dns record-sets transaction execute --zone=$DNS_ZONE

    gcloud beta compute ssl-certificates create $CERT1 --domains=$HOSTNAME1
    gcloud beta compute ssl-certificates create $CERT2 --domains=$HOSTNAME2

    # Creat SSL policy
    gcloud compute ssl-policies create $SSL_POLICY_NAME \
        --profile=COMPATIBLE \
        --min-tls-version=1.0
}

# create proxies
setup_domain_ip_and_certs
create_proxy 1 $IP1_ADDR 443 $CERT1 $K8_INSTANCE_GROUP $node_port1 $ZONE
create_proxy 2 $IP1_ADDR 8883 $CERT1 $K8_INSTANCE_GROUP $node_port2 $ZONE
create_proxy 3 $IP2_ADDR 443 $CERT2 $K8_INSTANCE_GROUP $node_port3 $ZONE
create_proxy 4 $IP2_ADDR 8883 $CERT2 $K8_INSTANCE_GROUP $node_port4 $ZONE

