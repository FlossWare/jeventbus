package org.flossware.jeventbus.api;

/**
 * Functional interface for handling messages received from the message bus.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * MessageHandler handler = message -> {
 *     String payload = new String(message.getPayload());
 *     System.out.println("Received: " + payload);
 * };
 *
 * messageBus.subscribe("my-topic", handler);
 * }</pre>
 *
 * @see MessageBus
 * @see Message
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * Called when a message is received on a subscribed topic.
     *
     * @param message the received message
     */
    void onMessage(Message message);
}
