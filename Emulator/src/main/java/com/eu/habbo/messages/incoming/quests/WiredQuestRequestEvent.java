package com.eu.habbo.messages.incoming.quests;
import com.eu.habbo.habbohotel.quests.*;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
public class WiredQuestRequestEvent extends MessageHandler {
    @Override public void handle() throws Exception {
        var manager=QuestManager.getInstance();int action=packet.readInt();String text=packet.readString();
        if(text.length()>10000)return;
        if(action==0){manager.send(client);return;}
        if(action==1){if(!manager.canEdit(client)){client.sendResponse(new com.eu.habbo.messages.outgoing.quests.WiredQuestStateComposer(manager.state(client,java.util.List.of(),"Permessi insufficienti")));return;}try{manager.save(client,WiredManager.getGson().fromJson(text,QuestDefinition.class));}catch(Exception e){client.sendResponse(new com.eu.habbo.messages.outgoing.quests.WiredQuestStateComposer(manager.state(client,java.util.List.of(),e instanceof IllegalArgumentException?e.getMessage():"Salvataggio non riuscito")));}return;}
        if(action==2){manager.mutate(client.getHabbo(),text,"give",0,false);return;}
        if(action==3){manager.mutate(client.getHabbo(),text,"remove",0,false);}
    }
    @Override public int getRatelimit(){return 0;}
}
