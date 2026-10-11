package com.eu.habbo.habbohotel.pets.breeding;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NestBreedingGuardTest {
    @Test void eitherParentOwnerCanConfirmWithTheSamePairInEitherOrder() {
        assertTrue(NestBreedingGuard.matches(10, 10, 20, 1, 2, 1, 2));
        assertTrue(NestBreedingGuard.matches(20, 10, 20, 1, 2, 2, 1));
    }
    @Test void roomRightsDoNotAuthorizeSomebodyElsesPets() {
        assertFalse(NestBreedingGuard.matches(30, 10, 20, 1, 2, 1, 2));
    }
    @Test void bothActualParentsMustMatchNotJustOne() {
        assertFalse(NestBreedingGuard.matches(10, 10, 20, 1, 2, 1, 999));
        assertFalse(NestBreedingGuard.matches(10, 10, 20, 1, 2, 1, 1));
        assertFalse(NestBreedingGuard.matches(10, 10, 20, 1, 1, 1, 1));
        assertFalse(NestBreedingGuard.matches(0, 10, 20, 1, 2, 1, 2));
    }
}
