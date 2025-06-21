package com.google.udmi.util.messaging;

/**
 * Config for a generic messaging client.
 *
 * @param protocol one of "pubsub" or "mqtt"
 * @param projectId gcp project id, required for pubsub protocol
 * @param brokerUrl mqtt broker url
 * @param publishTopicId topic where messages should be published, can be null to create a
 *     subscriber-only type of client
 * @param subscriptionId topic to be subscribed, can be null to create a publisher-only type of
 *     client
 */
public record MessagingClientConfig(String protocol, String projectId, String brokerUrl,
                                    String publishTopicId, String subscriptionId) {

}
