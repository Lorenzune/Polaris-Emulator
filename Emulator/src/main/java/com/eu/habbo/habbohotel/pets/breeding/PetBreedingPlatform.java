package com.eu.habbo.habbohotel.pets.breeding;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Dependencies installed by the emulator composition root; breeding code has no singleton lookups. */
public final class PetBreedingPlatform {
    public record Services(Supplier<ConfigurationManager> config, Supplier<Database> database,
            Supplier<GameEnvironment> game, Supplier<ThreadPooling> threading, Supplier<Random> random) {}
    private static final AtomicReference<Services> SERVICES = new AtomicReference<>();
    private PetBreedingPlatform() {}
    public static void install(Services services) { SERVICES.set(java.util.Objects.requireNonNull(services)); }
    public static Services services() { return java.util.Objects.requireNonNull(SERVICES.get(), "Breeding services not installed"); }
}
