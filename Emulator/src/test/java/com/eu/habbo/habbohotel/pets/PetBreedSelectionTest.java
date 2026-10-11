package com.eu.habbo.habbohotel.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PetBreedSelectionTest {
    private PetRace breed(int palette) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.getInt("color_one")).thenReturn(palette);
        when(result.getString("has_color_one")).thenReturn("1");
        when(result.getString("has_color_two")).thenReturn("0");
        return new PetRace(result);
    }

    @Test
    void randomCreationUsesRegisteredPaletteIdsEvenWhenTheyHaveGaps() throws Exception {
        var breeds = List.of(breed(1), breed(6), breed(28), breed(28));
        Random random = mock(Random.class);
        when(random.nextInt(3)).thenReturn(0, 1, 2);
        assertEquals(1, PetBreedSelection.choosePalette(breeds, random));
        assertEquals(6, PetBreedSelection.choosePalette(breeds, random));
        assertEquals(28, PetBreedSelection.choosePalette(breeds, random));
        verify(random, times(3)).nextInt(3);
    }

    @Test
    void missingBreedDataUsesBasePaletteWithoutNullOrEmptyRandomFailure() {
        Random random = mock(Random.class);
        assertEquals(0, PetBreedSelection.choosePalette(null, random));
        assertEquals(0, PetBreedSelection.choosePalette(List.of(), random));
        verifyNoInteractions(random);
    }
}
