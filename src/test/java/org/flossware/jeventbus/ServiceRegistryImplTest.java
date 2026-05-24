package org.flossware.jeventbus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

class ServiceRegistryImplTest {

    private ServiceRegistryImpl registry;

    interface TestService {
        String getName();
    }

    static class TestServiceImpl implements TestService {
        private final String name;

        TestServiceImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ServiceRegistryImpl();
    }

    @Test
    void testRegisterAndGetService() {
        TestService impl = new TestServiceImpl("test");
        registry.registerService(TestService.class, impl);

        Optional<TestService> found = registry.getService(TestService.class);

        assertTrue(found.isPresent());
        assertEquals("test", found.get().getName());
    }

    @Test
    void testRegisterMultipleImplementations() {
        TestService impl1 = new TestServiceImpl("impl1");
        TestService impl2 = new TestServiceImpl("impl2");
        TestService impl3 = new TestServiceImpl("impl3");

        registry.registerService(TestService.class, impl1);
        registry.registerService(TestService.class, impl2);
        registry.registerService(TestService.class, impl3);

        List<TestService> all = registry.getAllServices(TestService.class);

        assertEquals(3, all.size());
        assertEquals("impl1", all.get(0).getName());
        assertEquals("impl2", all.get(1).getName());
        assertEquals("impl3", all.get(2).getName());
    }

    @Test
    void testGetServiceReturnsFirst() {
        TestService impl1 = new TestServiceImpl("first");
        TestService impl2 = new TestServiceImpl("second");

        registry.registerService(TestService.class, impl1);
        registry.registerService(TestService.class, impl2);

        Optional<TestService> found = registry.getService(TestService.class);

        assertTrue(found.isPresent());
        assertEquals("first", found.get().getName());
    }

    @Test
    void testGetServiceNotFound() {
        Optional<TestService> found = registry.getService(TestService.class);

        assertFalse(found.isPresent());
    }

    @Test
    void testGetAllServicesNotFound() {
        List<TestService> all = registry.getAllServices(TestService.class);

        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void testUnregisterService() {
        TestService impl1 = new TestServiceImpl("impl1");
        TestService impl2 = new TestServiceImpl("impl2");

        registry.registerService(TestService.class, impl1);
        registry.registerService(TestService.class, impl2);

        assertEquals(2, registry.getAllServices(TestService.class).size());

        registry.unregisterService(TestService.class, impl1);

        List<TestService> remaining = registry.getAllServices(TestService.class);
        assertEquals(1, remaining.size());
        assertEquals("impl2", remaining.get(0).getName());
    }

    @Test
    void testUnregisterLastService() {
        TestService impl = new TestServiceImpl("only");
        registry.registerService(TestService.class, impl);

        registry.unregisterService(TestService.class, impl);

        assertFalse(registry.getService(TestService.class).isPresent());
    }

    @Test
    void testClear() {
        TestService impl1 = new TestServiceImpl("impl1");
        TestService impl2 = new TestServiceImpl("impl2");

        registry.registerService(TestService.class, impl1);
        registry.registerService(TestService.class, impl2);

        registry.clear();

        assertTrue(registry.getAllServices(TestService.class).isEmpty());
    }

    @Test
    void testRegisterNullServiceInterface() {
        assertThrows(NullPointerException.class, () -> {
            registry.registerService(null, new TestServiceImpl("test"));
        });
    }

    @Test
    void testRegisterNullImplementation() {
        assertThrows(NullPointerException.class, () -> {
            registry.registerService(TestService.class, null);
        });
    }

    @Test
    void testRegisterNonInterface() {
        assertThrows(IllegalArgumentException.class, () -> {
            registry.registerService(TestServiceImpl.class, new TestServiceImpl("test"));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRegisterWrongImplementation() {
        interface OtherService {}
        class OtherServiceImpl implements OtherService {}

        assertThrows(IllegalArgumentException.class, () -> {
            registry.registerService((Class) TestService.class, new OtherServiceImpl());
        });
    }
}
