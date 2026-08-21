package com.eu.habbo.messages.outgoing.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AvailableCommandsComposer extends MessageComposer {
    private final List<Command> commands;
    private final List<Rank> ranks;

    public AvailableCommandsComposer(List<Command> commands, List<Rank> ranks) {
        this.commands = commands;
        this.ranks = new ArrayList<>(ranks);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AvailableCommandsComposer);
        this.response.appendInt(this.commands.size());

        this.ranks.sort(Comparator.comparingInt(Rank::getLevel).thenComparingInt(Rank::getId));

        for (Command cmd : this.commands) {
            this.response.appendString(cmd.keys[0]);
            this.response.appendString(
                    Emulator.getTexts().getValueQuietly("commands.description." + cmd.permission, cmd.permission)
            );

            List<String> allowedRanks = new ArrayList<>();

            for (Rank rank : this.ranks) {
                Permission permission = rank.getPermissions().get(cmd.permission);

                if (permission == null || permission.setting == PermissionSetting.DISALLOWED) {
                    continue;
                }

                String label = rank.getName();
                if (permission.setting == PermissionSetting.ROOM_OWNER) {
                    label += " (proprietario stanza)";
                }

                allowedRanks.add(label);
            }

            this.response.appendInt(allowedRanks.size());
            for (String rankName : allowedRanks) {
                this.response.appendString(rankName);
            }
        }

        return this.response;
    }
}
