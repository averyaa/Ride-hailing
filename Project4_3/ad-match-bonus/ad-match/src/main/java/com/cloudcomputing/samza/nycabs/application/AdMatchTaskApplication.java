package com.cloudcomputing.samza.nycabs.application;

import com.cloudcomputing.samza.nycabs.AdMatchTask;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.samza.application.TaskApplication;
import org.apache.samza.application.descriptors.TaskApplicationDescriptor;
import org.apache.samza.serializers.JsonSerde;
import org.apache.samza.system.kafka.descriptors.KafkaInputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaSystemDescriptor;
import org.apache.samza.task.StreamTaskFactory;

import java.util.List;
import java.util.Map;

public class AdMatchTaskApplication implements TaskApplication {
    // Consider modify this zookeeper address, localhost may not be a good choice.
    // If this task application is executing in slave machine.
    private static final List<String> KAFKA_CONSUMER_ZK_CONNECT = ImmutableList.of("systems.kafka.consumer.zookeeper.connect");

    // Consider modify the bootstrap servers address. This example only cover one address.
    private static final List<String> KAFKA_PRODUCER_BOOTSTRAP_SERVERS = ImmutableList.of("systems.kafka.producer.bootstrap.servers");
    private static final Map<String, String> KAFKA_DEFAULT_STREAM_CONFIGS = ImmutableMap.of("replication.factor", "1");

    @Override
    public void describe(TaskApplicationDescriptor taskApplicationDescriptor) {
        // Define a system descriptor for Kafka.
        KafkaSystemDescriptor kafkaSystemDescriptor =
                new KafkaSystemDescriptor("kafka").withConsumerZkConnect(KAFKA_CONSUMER_ZK_CONNECT)
                        .withProducerBootstrapServers(KAFKA_PRODUCER_BOOTSTRAP_SERVERS)
                        .withDefaultStreamConfigs(KAFKA_DEFAULT_STREAM_CONFIGS);

        // Hint about streams, please refer to AdMatchConfig.java
        // We need one input stream "events", one output stream "ad-stream".

        // Input descriptor for the events
        KafkaInputDescriptor eventsInputDescriptor =
                kafkaSystemDescriptor.getInputDescriptor("events", new JsonSerde<>());

        // Set the default system descriptor to Kafka, so that it is used for all
        // internal resources, e.g., kafka topic for checkpointing, coordinator stream.
        taskApplicationDescriptor.withDefaultSystem(kafkaSystemDescriptor);

        taskApplicationDescriptor.withInputStream(eventsInputDescriptor);

        // Set the output
        taskApplicationDescriptor.withOutputStream(
                kafkaSystemDescriptor.getOutputDescriptor("ad-stream", new JsonSerde<>()));

        taskApplicationDescriptor.withTaskFactory((StreamTaskFactory) () -> new AdMatchTask());
    }
}
