package com.eu.habbo.habbohotel.users.levels;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Composition boundary for legacy no-argument handlers and static achievement entry points. */
public final class AvatarLevelPlatform {
    public record Services(
            Supplier<Database> database, Supplier<GameEnvironment> game, Supplier<ConfigurationManager> config) {}

    private static final AtomicReference<Services> SERVICES = new AtomicReference<>();

    private AvatarLevelPlatform() {}

    public static void install(Services services) {
        SERVICES.set(Objects.requireNonNull(services));
    }

    public static Database database() {
        return Objects.requireNonNull(SERVICES.get()).database().get();
    }

    public static GameEnvironment game() {
        return Objects.requireNonNull(SERVICES.get()).game().get();
    }

    public static ConfigurationManager config() {
        return Objects.requireNonNull(SERVICES.get()).config().get();
    }
}
