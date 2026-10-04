package com.eu.habbo.habbohotel.quests;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QuestManagerAuthorizationTest {
    private QuestManager manager(QuestDefinition definition) throws Exception {
        var ctor=QuestManager.class.getDeclaredConstructor();ctor.setAccessible(true);var manager=ctor.newInstance();
        var loaded=QuestManager.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.set(manager,true);
        var definitions=QuestManager.class.getDeclaredField("definitions");definitions.setAccessible(true);definitions.set(manager,Map.of(definition.code,definition));return manager;
    }
    @Test void onlyTheConfiguredRoomMayWriteTheQuestVariable() throws Exception {
        var d=new QuestDefinition();d.code="welcome";d.roomId=20;var manager=manager(d);
        var box=mock(WiredExtraQuest.class);when(box.getQuestCode()).thenReturn(d.code);when(box.getRoomId()).thenReturn(20);
        assertSame(d,manager.definitionForBox(box));when(box.getRoomId()).thenReturn(21);assertNull(manager.definitionForBox(box));
    }
    @Test void questAndChainCodesCannotBeSubstituted() throws Exception {
        var d=new QuestDefinition();d.code="welcome";d.roomId=20;var manager=manager(d);
        var box=mock(WiredExtraQuestChain.class);when(box.getQuestCode()).thenReturn(d.code);when(box.getRoomId()).thenReturn(20);assertNull(manager.definitionForBox(box));
    }
    @Test void definitionEditingRequiresStaffPermissionBeforeDatabaseAccess() throws Exception {
        var d=new QuestDefinition();d.code="welcome";var manager=manager(d);var client=mock(GameClient.class,RETURNS_DEEP_STUBS);
        assertThrows(IllegalArgumentException.class,()->manager.save(client,d));assertFalse(manager.state(client,List.of(),"").canEdit());
    }
}
