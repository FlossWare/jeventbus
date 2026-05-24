package org.flossware.jeventbus.api;

/**
 * Represents an active subscription to a message bus topic.
 * Use to manage the subscription lifecycle and check subscription status.
 *
 * <p>Example:</p>
 * <pre>{@code
 * Subscription sub = messageBus.subscribe("events", msg -> {...});
 * // ... receive messages ...
 * sub.cancel();  // Stop receiving messages
 * }</pre>
 *
 * @see MessageBus
 */
public interface Subscription {
    /**
     * Returns the topic this subscription is listening to.
     *
     * @return the topic name
     */
    String getTopic();

    /**
     * Cancels this subscription, stopping all future message delivery.
     * After calling this method, {@link #isActive()} will return false.
     */
    void cancel();

    /**
     * Checks if this subscription is still active and receiving messages.
     *
     * @return true if active, false if cancelled
     */
    boolean isActive();
}
