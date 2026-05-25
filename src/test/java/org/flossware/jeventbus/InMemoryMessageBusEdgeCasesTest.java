package org.flossware.jeventbus;

import org.flossware.jeventbus.api.Message;
import org.flossware.jeventbus.api.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for InMemoryMessageBus to achieve 100% coverage.
 */
class InMemoryMessageBusEdgeCasesTest {

    private InMemoryMessageBus messageBus;

    @AfterEach
    void tearDown() {
        if (messageBus != null) {
            messageBus.shutdown();
        }
    }

    @Test
    @DisplayName("Should handle double cancellation of subscription")
    void testDoubleCancellation() {
        messageBus = new InMemoryMessageBus();

        Subscription sub = messageBus.subscribe("test-topic", message -> {});

        assertTrue(sub.isActive());

        sub.cancel();
        assertFalse(sub.isActive());

        // Second cancel should be a no-op
        assertDoesNotThrow(() -> sub.cancel());
        assertFalse(sub.isActive());
    }

    @Test
    @DisplayName("Should not deliver messages to inactive subscriptions")
    void testInactiveSubscription() throws Exception {
        messageBus = new InMemoryMessageBus();

        AtomicBoolean messageReceived = new AtomicBoolean(false);
        CountDownLatch activeLatch = new CountDownLatch(1);

        // Subscribe two handlers
        Subscription inactiveSub = messageBus.subscribe("test-topic", message -> {
            messageReceived.set(true);
        });

        messageBus.subscribe("test-topic", message -> {
            activeLatch.countDown();
        });

        // Cancel first subscription before publishing
        inactiveSub.cancel();

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Test".getBytes())
                .build();

        messageBus.publish("test-topic", msg);

        // Wait for active subscription to receive
        assertTrue(activeLatch.await(5, TimeUnit.SECONDS));

        // Inactive subscription should not have received the message
        Thread.sleep(100); // Give time for any potential delivery
        assertFalse(messageReceived.get());
    }

    @Test
    @DisplayName("Should handle unsubscribe with custom Subscription implementation")
    void testUnsubscribeWithNonImplSubscription() {
        messageBus = new InMemoryMessageBus();

        Subscription customSub = new Subscription() {
            @Override
            public String getTopic() {
                return "test-topic";
            }

            @Override
            public void cancel() {
            }

            @Override
            public boolean isActive() {
                return true;
            }
        };

        // Should not throw exception even though it's not SubscriptionImpl
        assertDoesNotThrow(() -> messageBus.unsubscribe(customSub));
    }

    @Test
    @DisplayName("Should handle unsubscribe with SubscriptionImpl via messageBus.unsubscribe()")
    void testUnsubscribeWithSubscriptionImpl() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean messageReceived = new AtomicBoolean(false);

        // Subscribe and get the SubscriptionImpl
        Subscription subscription = messageBus.subscribe("test-topic", message -> {
            messageReceived.set(true);
            latch.countDown();
        });

        assertTrue(subscription.isActive());

        // Unsubscribe via messageBus.unsubscribe() instead of subscription.cancel()
        messageBus.unsubscribe(subscription);

        assertFalse(subscription.isActive());

        // Message should not be delivered after unsubscribe
        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Test".getBytes())
                .build();

        messageBus.publish("test-topic", msg);

        Thread.sleep(100); // Give time for any potential delivery
        assertFalse(messageReceived.get());
    }

    @Test
    @DisplayName("Should handle shutdown with pending messages")
    void testShutdownWithPendingMessages() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);

        // Create a handler that blocks
        messageBus.subscribe("test-topic", message -> {
            try {
                startedLatch.countDown();
                blockLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Test".getBytes())
                .build();

        messageBus.publish("test-topic", msg);

        // Wait for handler to start
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS));

        // Shutdown should complete even with pending message
        long shutdownStart = System.currentTimeMillis();
        messageBus.shutdown();
        long shutdownDuration = System.currentTimeMillis() - shutdownStart;

        // Shutdown should complete within reasonable time (10 seconds + overhead)
        assertTrue(shutdownDuration < 12000, "Shutdown took too long: " + shutdownDuration + "ms");

        blockLatch.countDown();
    }

    @Test
    @DisplayName("Should have unique subscription IDs")
    void testSubscriptionId() throws Exception {
        messageBus = new InMemoryMessageBus();

        Subscription sub1 = messageBus.subscribe("test-topic", message -> {});
        Subscription sub2 = messageBus.subscribe("test-topic", message -> {});

        // Use reflection to access private getId() method
        java.lang.reflect.Method getIdMethod = sub1.getClass().getDeclaredMethod("getId");
        getIdMethod.setAccessible(true);

        String id1 = (String) getIdMethod.invoke(sub1);
        String id2 = (String) getIdMethod.invoke(sub2);

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("Should handle cancellation of non-existent subscription")
    void testCancelNonExistentSubscription() {
        messageBus = new InMemoryMessageBus();

        Subscription sub = messageBus.subscribe("test-topic", message -> {});

        // Cancel once
        sub.cancel();

        // Manually remove from bus to simulate non-existent scenario
        // This tests the null check in removeSubscription
        assertDoesNotThrow(() -> sub.cancel());
    }

    @Test
    @DisplayName("Should get subscription topic")
    void testSubscriptionTopic() {
        messageBus = new InMemoryMessageBus();

        Subscription sub = messageBus.subscribe("my-test-topic", message -> {});

        assertEquals("my-test-topic", sub.getTopic());
    }

    @Test
    @DisplayName("Should get subscription handler")
    void testSubscriptionHandler() throws Exception {
        messageBus = new InMemoryMessageBus();

        Subscription sub = messageBus.subscribe("test-topic", message -> {});

        // Use reflection to access package-private getHandler() method
        java.lang.reflect.Method getHandlerMethod = sub.getClass().getDeclaredMethod("getHandler");
        getHandlerMethod.setAccessible(true);
        Object handler = getHandlerMethod.invoke(sub);

        assertNotNull(handler);
    }
}
