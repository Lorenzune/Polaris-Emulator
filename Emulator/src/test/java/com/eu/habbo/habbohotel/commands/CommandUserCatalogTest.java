package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandUserCatalogTest {
    @Test
    void publicAndInternalCommandsCannotBreakLoginCatalog() throws Exception {
        try (var fixture = new Registry()) {
            Command first = command("cmd_alpha", new String[] {"alpha"});
            fixture.add("alpha", first);
            fixture.add("public", command(null, new String[] {"wired"}));
            fixture.add("internal", command(null, new String[] {}));
            fixture.add("empty", command("cmd_empty", new String[] {}));
            fixture.add("null_alias", command("cmd_bad", new String[] {null}));
            fixture.add("blank_alias", command("cmd_bad", new String[] {""}));
            Habbo habbo = mock(Habbo.class);
            when(habbo.hasPermission("cmd_alpha", true)).thenReturn(true);
            var handler = mock(CommandHandler.class, CALLS_REAL_METHODS);
            assertEquals(List.of(first), assertDoesNotThrow(() -> handler.getCommandsForUser(habbo)));
        }
    }

    @Test
    void effectivePermissionFilteringSortingAndAliasDeduplicationArePreserved() throws Exception {
        try (var fixture = new Registry()) {
            Command z = command("cmd_zulu", new String[] {"z"}), a = command("cmd_alpha", new String[] {"a"});
            fixture.add("z", z);
            fixture.add("a", a);
            fixture.add("second_alias", a);
            fixture.add("denied", command("cmd_denied", new String[] {"denied"}));
            Habbo habbo = mock(Habbo.class);
            when(habbo.hasPermission("cmd_zulu", true)).thenReturn(true);
            when(habbo.hasPermission("cmd_alpha", true)).thenReturn(true);
            assertEquals(
                    List.of(a, z),
                    mock(CommandHandler.class, CALLS_REAL_METHODS).getCommandsForUser(habbo));
            verify(habbo).hasPermission("cmd_denied", true);
        }
    }

    @Test
    void catalogWithOnlyPublicCommandsIsEmptyAndDoesNotCheckPermissions() throws Exception {
        try (var fixture = new Registry()) {
            fixture.add("wired", command(null, new String[] {"wired"}));
            Habbo habbo = mock(Habbo.class);
            assertEquals(
                    List.of(), mock(CommandHandler.class, CALLS_REAL_METHODS).getCommandsForUser(habbo));
            verify(habbo, never()).hasPermission(anyString(), anyBoolean());
        }
    }

    private static Command command(String permission, String[] keys) {
        return new Command(permission, keys) {
            public boolean handle(GameClient client, String[] params) {
                return true;
            }
        };
    }

    private static final class Registry implements AutoCloseable {
        private final Map<String, Command> registry;
        private final Map<String, Command> saved;

        @SuppressWarnings("unchecked")
        Registry() throws Exception {
            var field = CommandHandler.class.getDeclaredField("commands");
            field.setAccessible(true);
            registry = (Map<String, Command>) field.get(null);
            saved = new HashMap<>(registry);
            registry.clear();
        }

        void add(String key, Command command) {
            CommandHandler.addCommand(key, command);
        }

        public void close() {
            registry.clear();
            registry.putAll(saved);
        }
    }
}
