package com.eu.habbo.habbohotel.pets.breeding;

public final class NestBreedingGuard {
    private NestBreedingGuard() {}
    public static boolean matches(int sender, int ownerOne, int ownerTwo,
            int actualOne, int actualTwo, int suppliedOne, int suppliedTwo) {
        return sender > 0 && (sender == ownerOne || sender == ownerTwo)
                && actualOne > 0 && actualTwo > 0 && actualOne != actualTwo
                && ((actualOne == suppliedOne && actualTwo == suppliedTwo)
                    || (actualOne == suppliedTwo && actualTwo == suppliedOne));
    }
}
