package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WiredQuestConfigurationTest {
    @Test void nativeQuestAndChainFieldsPersistWithoutCounterSubvariables() throws Exception {
        for (var box : new WiredExtraQuestBase[] { new WiredExtraQuest(1,1,mock(Item.class),"",0,0), new WiredExtraQuestChain(2,1,mock(Item.class),"",0,0) }) {
            var settings = new WiredSettings(new int[0], "My Quest\tWELCOME.1", new int[0], 0, 0);
            assertTrue(box.saveData(settings, null));
            assertEquals("my_quest",box.getVariableName()); assertEquals("WELCOME.1",box.getQuestCode());
            assertTrue(box.isNativeConfiguration()); assertTrue(box.getSelectedSubvariables().isEmpty()); assertFalse(box.hasSubvariable(0));
            var row = mock(java.sql.ResultSet.class); when(row.getString("wired_data")).thenReturn(box.getWiredData());
            box.onPickUp(); box.loadWiredData(row, mock(Room.class));
            assertEquals("my_quest",box.getVariableName()); assertEquals("WELCOME.1",box.getQuestCode());
        }
    }
    @Test void invalidQuestCodesDoNotReplaceSavedConfiguration() throws Exception {
        var box = new WiredExtraQuest(1,1,mock(Item.class),"",0,0);
        assertTrue(box.saveData(new WiredSettings(new int[0],"valid\tcode",new int[0],0,0),null));
        assertFalse(box.saveData(new WiredSettings(new int[0],"other\t",new int[0],0,0),null));
        assertFalse(box.saveData(new WiredSettings(new int[0],"other\tcode\nsecond",new int[0],0,0),null));
        assertEquals("valid",box.getVariableName());
    }
    @Test void legacyTargetsAndDerivedValuesRemainReadable() throws Exception {
        var box = new WiredExtraQuest(1,1,mock(Item.class),"",0,0);
        var row=mock(java.sql.ResultSet.class); when(row.getString("wired_data")).thenReturn("{\"targetValue\":10}");
        box.loadWiredData(row,mock(Room.class)); assertFalse(box.isNativeConfiguration());
        assertEquals(10,box.getTargetValue()); assertEquals(5,box.getSelectedSubvariables().size());
        assertEquals(50,box.derive(WiredExtraQuest.SUB_PERCENT,5));
    }
    @Test void nativeQuestIsAWritableUserVariableBackedByPersistentQuestProgress() throws Exception {
        var box=new WiredExtraQuest(1,1,mock(Item.class),"",0,0);
        box.saveData(new WiredSettings(new int[0],"welcome_progress\tWELCOME.1",new int[0],0,0),null);
        var room=mock(Room.class,RETURNS_DEEP_STUBS);
        when(room.getRoomSpecialTypes().getExtras()).thenReturn(java.util.Set.of(box));
        when(room.getRoomSpecialTypes().getExtra(1)).thenReturn(box);
        var service=mock(com.eu.habbo.habbohotel.quests.QuestManager.class);
        var definition=new com.eu.habbo.habbohotel.quests.QuestDefinition();definition.code="WELCOME.1";
        when(service.definitionForBox(box)).thenReturn(definition);
        var progress=new com.eu.habbo.habbohotel.quests.QuestProgress();progress.active=true;progress.value=3;
        when(service.progress(7,"WELCOME.1")).thenReturn(progress);
        try(var mocked=mockStatic(com.eu.habbo.habbohotel.quests.QuestManager.class)) {
            mocked.when(com.eu.habbo.habbohotel.quests.QuestManager::getInstance).thenReturn(service);
            var manager=new com.eu.habbo.habbohotel.rooms.RoomUserVariableManager(room);
            assertNotNull(manager.getDefinitionInfo(1));assertFalse(manager.getDefinitionInfo(1).isReadOnly());
            assertEquals(1,manager.getAllDefinitionInfos().size());assertTrue(manager.hasVariable(7,1));assertEquals(3,manager.getCurrentValue(7,1));
            var user=mock(com.eu.habbo.habbohotel.users.Habbo.class);when(room.getHabbo(7)).thenReturn(user);
            manager.assignVariable(user,1,0,false);verify(service).mutate(user,box,"give",0,false);
            manager.updateVariableValue(7,1,4);verify(service).mutate(user,box,"change",4,true);
            manager.removeVariable(7,1);verify(service).mutate(user,box,"remove",0,true);
        }
    }
}
