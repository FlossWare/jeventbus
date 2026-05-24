package org.flossware.jeventbus;

import org.flossware.jeventbus.api.Message;
import org.flossware.jeventbus.api.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class InMemoryMessageBusTest {

    private InMemoryMessageBus messageBus;

    @AfterEach
    void tearDown() {
        if (messageBus != null) {
            messageBus.shutdown();
        }
    }

    @Test
    void testPublishAndReceive() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> received = new AtomicReference<>();

        Subscription sub = messageBus.subscribe("test-topic", message -> {
            received.set(message);
            latch.countDown();
        });

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Hello".getBytes())
                .build();

        messageBus.publish("test-topic", msg);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals("test-topic", received.get().getTopic());
        assertEquals("test-app", received.get().getSourceApplicationId());
        assertArrayEquals("Hello".getBytes(), received.get().getPayload());
    }

    @Test
    void testMultipleSubscribers() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            messageBus.subscribe("test-topic", message -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Broadcast".getBytes())
                .build();

        messageBus.publish("test-topic", msg);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, counter.get());
    }

    @Test
    void testUnsubscribe() throws Exception {
        messageBus = new InMemoryMessageBus();

        AtomicInteger counter = new AtomicInteger(0);
        Subscription sub = messageBus.subscribe("test-topic", message -> counter.incrementAndGet());

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Test".getBytes())
                .build();

        messageBus.publish("test-topic", msg);
        Thread.sleep(100);

        int firstCount = counter.get();
        assertTrue(firstCount > 0);

        sub.cancel();
        assertFalse(sub.isActive());

        messageBus.publish("test-topic", msg);
        Thread.sleep(100);

        assertEquals(firstCount, counter.get());
    }

    @Test
    void testPublishWithNoSubscribers() {
        messageBus = new InMemoryMessageBus();

        Message msg = Message.builder()
                .topic("no-subscribers")
                .sourceApplicationId("test-app")
                .payload("Nobody listening".getBytes())
                .build();

        assertDoesNotThrow(() -> messageBus.publish("no-subscribers", msg));
    }

    @Test
    void testMultipleTopics() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch topic1Latch = new CountDownLatch(1);
        CountDownLatch topic2Latch = new CountDownLatch(1);
        AtomicReference<String> topic1Msg = new AtomicReference<>();
        AtomicReference<String> topic2Msg = new AtomicReference<>();

        messageBus.subscribe("topic1", message -> {
            topic1Msg.set(new String(message.getPayload()));
            topic1Latch.countDown();
        });

        messageBus.subscribe("topic2", message -> {
            topic2Msg.set(new String(message.getPayload()));
            topic2Latch.countDown();
        });

        messageBus.publish("topic1", Message.builder()
                .topic("topic1")
                .sourceApplicationId("test-app")
                .payload("Message 1".getBytes())
                .build());

        messageBus.publish("topic2", Message.builder()
                .topic("topic2")
                .sourceApplicationId("test-app")
                .payload("Message 2".getBytes())
                .build());

        assertTrue(topic1Latch.await(5, TimeUnit.SECONDS));
        assertTrue(topic2Latch.await(5, TimeUnit.SECONDS));
        assertEquals("Message 1", topic1Msg.get());
        assertEquals("Message 2", topic2Msg.get());
    }

    @Test
    void testMessageWithHeaders() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> received = new AtomicReference<>();

        messageBus.subscribe("test-topic", message -> {
            received.set(message);
            latch.countDown();
        });

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Data".getBytes())
                .header("priority", "high")
                .header("version", 1)
                .build();

        messageBus.publish("test-topic", msg);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("high", received.get().getHeaders().get("priority"));
        assertEquals(1, received.get().getHeaders().get("version"));
    }

    @Test
    void testHandlerException() throws Exception {
        messageBus = new InMemoryMessageBus();

        CountDownLatch goodLatch = new CountDownLatch(1);
        CountDownLatch badLatch = new CountDownLatch(1);

        messageBus.subscribe("test-topic", message -> {
            badLatch.countDown();
            throw new RuntimeException("Handler error");
        });

        messageBus.subscribe("test-topic", message -> goodLatch.countDown());

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Test".getBytes())
                .build();

        messageBus.publish("test-topic", msg);

        assertTrue(badLatch.await(5, TimeUnit.SECONDS));
        assertTrue(goodLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testShutdown() throws Exception {
        messageBus = new InMemoryMessageBus();

        Message msg = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("Test".getBytes())
                .build();

        messageBus.subscribe("test-topic", message -> {});
        messageBus.publish("test-topic", msg);

        assertDoesNotThrow(() -> messageBus.shutdown());
    }
}
