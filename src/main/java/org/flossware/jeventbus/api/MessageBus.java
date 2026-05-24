package org.flossware.jeventbus.api;

/**
 * Publish/subscribe message bus for inter-application communication.
 * Enables applications to send and receive messages through named topics.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Subscribe to messages
 * Subscription sub = messageBus.subscribe("events", message -> {
 *     System.out.println("Received: " + new String(message.getPayload()));
 * });
 *
 * // Publish a message
 * Message msg = Message.builder()
 *     .topic("events")
 *     .payload("Hello".getBytes())
 *     .build();
 * messageBus.publish("events", msg);
 *
 * // Unsubscribe when done
 * messageBus.unsubscribe(sub);
 * }</pre>
 *
 * @see Message
 * @see MessageHandler
 * @see Subscription
 */
public interface MessageBus {
    /**
     * Publishes a message to all subscribers of the specified topic.
     * Message delivery is asynchronous and does not block the publisher.
     *
     * @param topic the topic to publish to
     * @param message the message to publish
     */
    void publish(String topic, Message message);

    /**
     * Subscribes to receive messages on the specified topic.
     * The handler will be invoked asynchronously for each received message.
     *
     * @param topic the topic to subscribe to
     * @param handler the handler to invoke for received messages
     * @return a subscription that can be used to unsubscribe
     */
    Subscription subscribe(String topic, MessageHandler handler);

    /**
     * Unsubscribes from receiving messages.
     * After this call, the handler will no longer receive messages.
     *
     * @param subscription the subscription to cancel
     */
    void unsubscribe(Subscription subscription);
}
