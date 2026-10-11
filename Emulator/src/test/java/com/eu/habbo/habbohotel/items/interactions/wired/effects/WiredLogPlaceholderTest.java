package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WiredLogPlaceholderTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void resolvesStackVariableAtExecutionWithoutChangingTemplate(int variant) throws Exception {
        Item base = mock(Item.class);
        InteractionWiredEffect effect =
                switch (variant) {
                    case 0 -> new WiredEffectLog(123, 1, base, "", 0, 0);
                    case 1 -> new WiredEffectNegativeLog(123, 1, base, "", 0, 0);
                    case 2 -> new WiredEffectWriteToLogs(123, 1, base, "", 0, 0);
                    default -> new WiredEffectNegativeWriteToLogs(123, 1, base, "", 0, 0);
                };
        effect.saveData(new WiredSettings(new int[] {0}, "Valore: $(test)", new int[0], 0), null);
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        RoomVariableManager variables = mock(RoomVariableManager.class);
        when(room.getId()).thenReturn(7);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        when(room.getRoomVariableManager()).thenReturn(variables);
        HabboItem trigger = mock(HabboItem.class);
        when(trigger.getX()).thenReturn((short) 11);
        when(trigger.getY()).thenReturn((short) 12);
        WiredContext context = mock(WiredContext.class);
        when(context.room()).thenReturn(room);
        when(context.triggerItem()).thenReturn(trigger);
        WiredExtraTextOutputVariable extra = mock(WiredExtraTextOutputVariable.class);
        when(extra.getPlaceholderToken()).thenReturn("$(test)");
        when(extra.getTargetType()).thenReturn(WiredExtraTextOutputVariable.TARGET_ROOM);
        when(extra.getVariableToken()).thenReturn("55");
        when(extra.getVariableItemId()).thenReturn(55);
        when(extra.getDisplayType(room)).thenReturn(WiredExtraTextOutputVariable.DISPLAY_NUMERIC);
        when(types.getExtras(11, 12)).thenReturn(Set.of(extra));
        try (var manager = mockStatic(WiredManager.class)) {
            when(variables.getCurrentValue(55)).thenReturn(42);
            effect.execute(context);
            manager.verify(() -> WiredManager.recordWiredLog(7, 123, "Valore: 42", WiredRoomDiagnostics.Severity.INFO));
            when(variables.getCurrentValue(55)).thenReturn(99);
            effect.execute(context);
            manager.verify(() -> WiredManager.recordWiredLog(7, 123, "Valore: 99", WiredRoomDiagnostics.Severity.INFO));
            when(types.getExtras(11, 12)).thenReturn(Set.of());
            effect.execute(context);
            manager.verify(
                    () -> WiredManager.recordWiredLog(7, 123, "Valore: $(test)", WiredRoomDiagnostics.Severity.INFO));
            when(types.getExtras(effect.getX(), effect.getY())).thenReturn(Set.of(extra));
            effect.execute(null, room, null);
            manager.verify(
                    () -> WiredManager.recordWiredLog(7, 123, "Valore: 99", WiredRoomDiagnostics.Severity.INFO),
                    times(2));
        }
    }
}
