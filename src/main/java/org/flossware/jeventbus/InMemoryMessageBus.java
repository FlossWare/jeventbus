package org.flossware.jeventbus;

import org.flossware.jeventbus.api.Message;
import org.flossware.jeventbus.api.MessageBus;
import org.flossware.jeventbus.api.MessageHandler;
import org.flossware.jeventbus.api.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * In-memory message bus for inter-application communication.
 * Uses asynchronous dispatch to prevent blocking publishers.
 * <p>
 * This implementation provides a topic-based publish-subscribe messaging system
 * where publishers can send messages to topics and subscribers receive messages
 * from topics they're interested in. Message delivery is asynchronous and uses
 * a fixed thread pool to dispatch messages to handlers.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. Uses ConcurrentHashMap for subscribers
 * map and CopyOnWriteArrayList for subscriber lists. All operations are thread-safe.
 * <p>
 * Example usage:
 * {@code
 * InMemoryMessageBus messageBus = new InMemoryMessageBus();
 *
 * // Subscribe to a topic
 * Subscription subscription = messageBus.subscribe("my-topic", message -> {
 *     System.out.println("Received: " + new String(message.getPayload()));
 * });
 *
 * // Publish a message
 * Message message = Message.builder()
 *     .topic("my-topic")
 *     .sourceApplicationId("my-app")
 *     .payload("Hello, World!".getBytes())
 *     .build();
 * messageBus.publish("my-topic", message);
 *
 * // Unsubscribe
 * subscription.cancel();
 *
 * // When done
 * messageBus.shutdown();
 * }
 *
 * @see MessageBus
 * @see Message
 * @see Subscription
 */
public class InMemoryMessageBus implements MessageBus {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryMessageBus.class);
    private static final List<SubscriptionImpl> EMPTY_SUBSCRIBERS = Collections.emptyList();

    private final Map<String, CopyOnWriteArrayList<SubscriptionImpl>> subscribers;
    private final ExecutorService dispatchExecutor;

    /**
     * Creates a new in-memory message bus.
     * <p>
     * Initializes a fixed thread pool with 4 threads for asynchronous message dispatch.
     * Dispatcher threads are daemon threads to not prevent JVM shutdown.
     */
    public InMemoryMessageBus() {
        this.subscribers = new ConcurrentHashMap<>();
        this.dispatchExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "message-bus-dispatcher");
            t.setDaemon(true);
            return t;
        });
        logger.info("InMemoryMessageBus started");
    }

    /**
     * Publishes a message to the specified topic.
     * <p>
     * The message is delivered asynchronously to all active subscribers of the topic.
     * If there are no subscribers, the message is discarded. Exceptions in message
     * handlers are caught and logged but do not affect other subscribers.
     *
     * @param topic the topic to publish to
     * @param message the message to publish
     * @throws NullPointerException if topic or message is null
     */
    @Override
    public void publish(String topic, Message message) {
        List<SubscriptionImpl> topicSubscribers = subscribers.get(topic);

        if (topicSubscribers == null || topicSubscribers.isEmpty()) {
            logger.debug("No subscribers for topic: {}", topic);
            return;
        }

        logger.debug("Publishing message to topic '{}': {} subscribers", topic, topicSubscribers.size());

        for (SubscriptionImpl subscription : topicSubscribers) {
            if (subscription.isActive()) {
                dispatchExecutor.submit(() -> {
                    try {
                        subscription.getHandler().onMessage(message);
                    } catch (Exception e) {
                        logger.error("Error delivering message to subscriber on topic '{}'", topic, e);
                    }
                });
            }
        }
    }

    /**
     * Subscribes to messages on the specified topic.
     * <p>
     * The handler will be invoked asynchronously for each message published to the topic.
     * The subscription remains active until {@link Subscription#cancel()} is called.
     *
     * @param topic the topic to subscribe to
     * @param handler the handler to invoke for each message
     * @return a subscription that can be used to cancel the subscription
     * @throws NullPointerException if topic or handler is null
     */
    @Override
    public Subscription subscribe(String topic, MessageHandler handler) {
        SubscriptionImpl subscription = new SubscriptionImpl(topic, handler, this);

        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(subscription);

        logger.info("New subscription to topic '{}'. Total subscribers: {}",
                topic, subscribers.get(topic).size());

        return subscription;
    }

    /**
     * Unsubscribes the specified subscription.
     * <p>
     * After this call, the subscription's handler will no longer receive messages.
     * This is equivalent to calling {@link Subscription#cancel()}.
     *
     * @param subscription the subscription to cancel
     */
    @Override
    public void unsubscribe(Subscription subscription) {
        if (subscription instanceof SubscriptionImpl) {
            SubscriptionImpl impl = (SubscriptionImpl) subscription;
            impl.cancel();
        }
    }

    /**
     * Internal method to remove a subscription from the subscribers map.
     * <p>
     * This is called by SubscriptionImpl when cancelled.
     *
     * @param subscription the subscription to remove
     */
    void removeSubscription(SubscriptionImpl subscription) {
        CopyOnWriteArrayList<SubscriptionImpl> topicSubscribers =
                subscribers.get(subscription.getTopic());

        if (topicSubscribers != null) {
            topicSubscribers.remove(subscription);
            logger.info("Unsubscribed from topic '{}'. Remaining subscribers: {}",
                    subscription.getTopic(), topicSubscribers.size());
        }
    }

    /**
     * Shuts down the message bus.
     * <p>
     * Initiates an orderly shutdown of the dispatch executor, waiting up to 10 seconds
     * for pending message deliveries to complete. If the executor does not terminate
     * within 10 seconds, a forced shutdown is initiated.
     */
    public void shutdown() {
        logger.info("Shutting down message bus");
        dispatchExecutor.shutdown();
        try {
            if (!dispatchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class SubscriptionImpl implements Subscription {
        private final String id;
        private final String topic;
        private final MessageHandler handler;
        private final InMemoryMessageBus bus;
        private volatile boolean active = true;

        SubscriptionImpl(String topic, MessageHandler handler, InMemoryMessageBus bus) {
            this.id = UUID.randomUUID().toString();
            this.topic = topic;
            this.handler = handler;
            this.bus = bus;
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public void cancel() {
            if (active) {
                active = false;
                bus.removeSubscription(this);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        public MessageHandler getHandler() {
            return handler;
        }

        public String getId() {
            return id;
        }
    }
}
