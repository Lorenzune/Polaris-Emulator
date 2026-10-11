package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import java.lang.reflect.Method;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WiredEffectChangeVariableValueRandomTest {
    @Test
    void randomOperationIncludesZeroAndTheMaximumIntegerWithoutOverflow() throws Exception {
        var effect = effect();
        Method apply = operation();
        Random random = mock(Random.class);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(random);
            when(random.nextLong(2L)).thenReturn(0L, 1L);
            assertEquals(0, apply.invoke(effect, 10, 1));
            assertEquals(1, apply.invoke(effect, 10, 1));
            when(random.nextLong(2147483648L)).thenReturn((long) Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, apply.invoke(effect, 10, Integer.MAX_VALUE));
            verify(random).nextLong(2147483648L);
        }
    }

    @Test
    void missingAndNonPositiveBoundsDoNotUseTheRandomGenerator() throws Exception {
        var effect = effect();
        Method apply = operation();
        Random random = mock(Random.class);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(random);
            for (Integer bound : new Integer[] {null, 0, -1}) {
                assertEquals(0, apply.invoke(effect, 10, bound));
            }
            verifyNoInteractions(random);
        }
    }

    private WiredEffectChangeVariableValue effect() throws Exception {
        var effect = new WiredEffectChangeVariableValue(1, 1, mock(Item.class), "", 0, 0);
        var field = WiredEffectChangeVariableValue.class.getDeclaredField("operation");
        field.setAccessible(true);
        field.setInt(effect, WiredEffectChangeVariableValue.OP_RANDOM);
        return effect;
    }

    private Method operation() throws Exception {
        var method = WiredEffectChangeVariableValue.class.getDeclaredMethod("applyOperation", int.class, Integer.class);
        method.setAccessible(true);
        return method;
    }
}
