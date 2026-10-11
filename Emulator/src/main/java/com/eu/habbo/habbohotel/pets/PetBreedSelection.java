package com.eu.habbo.habbohotel.pets;

import java.util.Collection;
import java.util.Random;

final class PetBreedSelection {
    private PetBreedSelection() {}

    static int choosePalette(Collection<PetRace> breeds, Random random) {
        if (breeds == null || breeds.isEmpty()) return 0;
        int[] palettes = breeds.stream().mapToInt(breed -> breed.colorOne).distinct().sorted().toArray();
        return palettes[random.nextInt(palettes.length)];
    }
}
