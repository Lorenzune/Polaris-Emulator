package com.eu.habbo.habbohotel.pets.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.jupiter.api.Test;

class PetBreedingOddsTest {
    @Test
    void percentagesCoverTheClampedTailAndAddUpToOneHundred() {
        for (int level = 1; level <= 20; level++) {
            var distribution = new NormalDistribution(level, (20 - level / 2.0) / 2);
            int[] odds = PetBreedingOdds.percentages(distribution);
            assertEquals(100, java.util.Arrays.stream(odds).sum());
            for (int chance : odds) assertTrue(chance >= 0 && chance <= 100);
            assertEquals(100 - (int) (distribution.cumulativeProbability(18.5) * 100), odds[3]);
        }
    }
}
