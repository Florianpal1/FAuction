package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImporterApi;
import fr.florianpal.fauction.api.importer.ImporterRegistry;
import fr.florianpal.fauction.testing.FakeImporter;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImporterRegistryImplTest {

    private ServerMock server;

    private Plugin owner;

    private Plugin other;

    private ImporterRegistryImpl registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        owner = MockBukkit.createMockPlugin("Owner");
        other = MockBukkit.createMockPlugin("Other");
        registry = new ImporterRegistryImpl(Logger.getLogger("RegistryTest"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Registered modules are found by id and listed sorted")
    void registerFindList() {
        FakeImporter b = new FakeImporter("b-module");
        FakeImporter a = new FakeImporter("a_module");
        registry.register(owner, b);
        registry.register(owner, a);

        assertSame(a, registry.find("a_module").orElseThrow());
        assertEquals(List.of(a, b), registry.list());
        assertTrue(registry.find("missing").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Upper", "with space", "dots.no", "a-very-long-identifier-of-more-than-32"})
    @DisplayName("An invalid id is refused")
    void invalidIdIsRefused(String id) {
        assertThrows(IllegalArgumentException.class, () -> registry.register(owner, new FakeImporter(id)));
        assertTrue(registry.list().isEmpty());
    }

    @Test
    @DisplayName("An id already taken is refused, the first module stays")
    void duplicateIdIsRefused() {
        FakeImporter first = new FakeImporter("same");
        registry.register(owner, first);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registry.register(other, new FakeImporter("same")));
        assertTrue(error.getMessage().contains("Owner"));
        assertSame(first, registry.find("same").orElseThrow());
    }

    @Test
    @DisplayName("A module needing a newer API is refused")
    void tooRecentApiIsRefused() {
        FakeImporter module = new FakeImporter("future").requiredApiVersion(ImporterApi.API_VERSION + 1);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registry.register(owner, module));
        assertTrue(error.getMessage().contains("update FAuction"));

        registry.register(owner, new FakeImporter("current").requiredApiVersion(ImporterApi.API_VERSION));
        assertTrue(registry.find("current").isPresent());
    }

    @Test
    @DisplayName("Disabling a plugin drops its modules, and only its own")
    void disablingTheOwnerUnregisters() {
        registry.register(owner, new FakeImporter("mine"));
        registry.register(other, new FakeImporter("theirs"));
        server.getPluginManager().registerEvents(registry, owner);

        server.getPluginManager().callEvent(new PluginDisableEvent(other));

        assertTrue(registry.find("mine").isPresent());
        assertTrue(registry.find("theirs").isEmpty());
    }

    @Test
    @DisplayName("Unregistering needs the very module that was registered")
    void unregister() {
        FakeImporter module = new FakeImporter("x");
        registry.register(owner, module);

        assertFalse(registry.unregister(new FakeImporter("x")));
        assertTrue(registry.unregister(module));
        assertFalse(registry.unregister(module));
        // The id is free again.
        registry.register(other, new FakeImporter("x"));
    }

    @Test
    @DisplayName("The registry is reachable through the ServicesManager")
    void visibleThroughTheServicesManager() {
        server.getServicesManager().register(ImporterRegistry.class, registry, owner, ServicePriority.Normal);

        ImporterRegistry loaded = server.getServicesManager().load(ImporterRegistry.class);
        assertSame(registry, loaded);
        loaded.register(other, new FakeImporter("external"));
        assertTrue(registry.find("external").isPresent());
    }
}
