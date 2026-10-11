package com.eu.habbo.habbohotel.pets.breeding;

import org.apache.commons.math3.distribution.NormalDistribution;

public final class PetBreedingOdds {
    private PetBreedingOdds() {}
    /** Common through legendary; boundaries match rounding and clamping the sampled score. */
    public static int[] percentages(NormalDistribution distribution) {
        int common = (int) (distribution.cumulativeProbability(10.5) * 100);
        int uncommonAndCommon = (int) (distribution.cumulativeProbability(15.5) * 100);
        int allButLegendary = (int) (distribution.cumulativeProbability(18.5) * 100);
        return new int[] {common, uncommonAndCommon - common, allButLegendary - uncommonAndCommon, 100 - allButLegendary
        };
    }
}
