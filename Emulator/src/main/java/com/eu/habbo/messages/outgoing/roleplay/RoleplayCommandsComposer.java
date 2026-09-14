package com.eu.habbo.messages.outgoing.roleplay;

import com.eu.habbo.habbohotel.commands.roleplay.RoleplayCommandData;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class RoleplayCommandsComposer extends MessageComposer {

    private final Collection<RoleplayCommandData> commands;

    public RoleplayCommandsComposer(Collection<RoleplayCommandData> commands) {
        this.commands = commands;
    }

    public RoleplayCommandsComposer(Map<Integer, ?> commandsMap) {
        ArrayList<RoleplayCommandData> list = new ArrayList<>();
        if (commandsMap != null) {
            for (Object obj : commandsMap.values()) {
                if (obj instanceof com.eu.habbo.habbohotel.commands.roleplay.GenericRoleplayCommand roleplayCmd) {
                    list.add(roleplayCmd.getData());
                } else if (obj instanceof RoleplayCommandData data) {
                    list.add(data);
                }
            }
        }
        this.commands = list;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoleplayCommandsComposer);

        if (this.commands == null || this.commands.isEmpty()) {
            this.response.appendInt(0);
            return this.response;
        }

        this.response.appendInt(this.commands.size());

        for (RoleplayCommandData cmd : this.commands) {
            this.response.appendInt(cmd.getId());
            this.response.appendString(cmd.getName());
            this.response.appendString((cmd.getKeys() != null && cmd.getKeys().length > 0) ? cmd.getKeys()[0] : "");
            this.response.appendString(cmd.getCategory() != null ? cmd.getCategory() : "");
            this.response.appendBoolean(cmd.isRequiresTarget());
            this.response.appendBoolean(cmd.isAllowSelf());
            this.response.appendInt(cmd.getMinRank());
            this.response.appendInt(cmd.getSortOrder());
        }

        return this.response;
    }
}
