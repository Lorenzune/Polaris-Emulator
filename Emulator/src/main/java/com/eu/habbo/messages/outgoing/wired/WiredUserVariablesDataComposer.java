package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFxBase;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAchievementEnabler;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class WiredUserVariablesDataComposer extends MessageComposer {
    private final RoomUserVariableManager.Snapshot userSnapshot;
    private final RoomFurniVariableManager.Snapshot furniSnapshot;
    private final RoomVariableManager.Snapshot roomSnapshot;
    private final List<WiredVariableDefinitionInfo> contextDefinitions;

    public WiredUserVariablesDataComposer(RoomUserVariableManager.Snapshot userSnapshot, RoomFurniVariableManager.Snapshot furniSnapshot, RoomVariableManager.Snapshot roomSnapshot) {
        this(userSnapshot, furniSnapshot, roomSnapshot, resolveContextDefinitions(userSnapshot, furniSnapshot, roomSnapshot));
    }

    public WiredUserVariablesDataComposer(RoomUserVariableManager.Snapshot userSnapshot, RoomFurniVariableManager.Snapshot furniSnapshot, RoomVariableManager.Snapshot roomSnapshot, List<WiredVariableDefinitionInfo> contextDefinitions) {
        this.userSnapshot = userSnapshot;
        this.furniSnapshot = furniSnapshot;
        this.roomSnapshot = roomSnapshot;
        this.contextDefinitions = (contextDefinitions != null) ? contextDefinitions : Collections.emptyList();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredUserVariablesDataComposer);

        int roomId = 0;

        if (this.userSnapshot != null) {
            roomId = this.userSnapshot.getRoomId();
        } else if (this.furniSnapshot != null) {
            roomId = this.furniSnapshot.getRoomId();
        } else if (this.roomSnapshot != null) {
            roomId = this.roomSnapshot.getRoomId();
        }

        this.response.appendInt(roomId);

        this.response.appendInt((this.userSnapshot != null) ? this.userSnapshot.getDefinitions().size() : 0);

        if (this.userSnapshot != null) {
            for (RoomUserVariableManager.DefinitionEntry definition : this.userSnapshot.getDefinitions()) {
                this.response.appendInt(definition.getItemId());
                this.response.appendString(definition.getName());
                this.response.appendBoolean(definition.hasValue());
                this.response.appendInt(definition.getAvailability());
                this.response.appendBoolean(definition.isTextConnected());
                this.response.appendBoolean(definition.isReadOnly());
            }
        }

        this.response.appendInt((this.userSnapshot != null) ? this.userSnapshot.getUsers().size() : 0);

        if (this.userSnapshot != null) {
            for (RoomUserVariableManager.UserAssignmentsEntry user : this.userSnapshot.getUsers()) {
                this.response.appendInt(user.getUserId());
                this.response.appendInt(user.getAssignments().size());

                for (RoomUserVariableManager.AssignmentEntry assignment : user.getAssignments()) {
                    this.response.appendInt(assignment.getVariableItemId());
                    this.response.appendBoolean(assignment.hasValue());
                    this.response.appendInt((assignment.getValue() != null) ? assignment.getValue() : 0);
                    this.response.appendInt(assignment.getCreatedAt());
                    this.response.appendInt(assignment.getUpdatedAt());
                }
            }
        }

        this.response.appendInt((this.furniSnapshot != null) ? this.furniSnapshot.getDefinitions().size() : 0);

        if (this.furniSnapshot != null) {
            for (RoomFurniVariableManager.DefinitionEntry definition : this.furniSnapshot.getDefinitions()) {
                this.response.appendInt(definition.getItemId());
                this.response.appendString(definition.getName());
                this.response.appendBoolean(definition.hasValue());
                this.response.appendInt(definition.getAvailability());
                this.response.appendBoolean(definition.isTextConnected());
                this.response.appendBoolean(definition.isReadOnly());
            }
        }

        this.response.appendInt((this.furniSnapshot != null) ? this.furniSnapshot.getFurnis().size() : 0);

        if (this.furniSnapshot != null) {
            for (RoomFurniVariableManager.FurniAssignmentsEntry furni : this.furniSnapshot.getFurnis()) {
                this.response.appendInt(furni.getFurniId());
                this.response.appendInt(furni.getAssignments().size());

                for (RoomFurniVariableManager.AssignmentEntry assignment : furni.getAssignments()) {
                    this.response.appendInt(assignment.getVariableItemId());
                    this.response.appendBoolean(assignment.hasValue());
                    this.response.appendInt((assignment.getValue() != null) ? assignment.getValue() : 0);
                    this.response.appendInt(assignment.getCreatedAt());
                    this.response.appendInt(assignment.getUpdatedAt());
                }
            }
        }

        this.response.appendInt((this.roomSnapshot != null) ? this.roomSnapshot.getDefinitions().size() : 0);

        if (this.roomSnapshot != null) {
            for (RoomVariableManager.DefinitionEntry definition : this.roomSnapshot.getDefinitions()) {
                this.response.appendInt(definition.getItemId());
                this.response.appendString(definition.getName());
                this.response.appendBoolean(definition.hasValue());
                this.response.appendInt(definition.getAvailability());
                this.response.appendBoolean(definition.isTextConnected());
                this.response.appendBoolean(definition.isReadOnly());
            }
        }

        this.response.appendInt((this.roomSnapshot != null) ? this.roomSnapshot.getAssignments().size() : 0);

        if (this.roomSnapshot != null) {
            for (RoomVariableManager.AssignmentEntry assignment : this.roomSnapshot.getAssignments()) {
                this.response.appendInt(assignment.getVariableItemId());
                this.response.appendBoolean(assignment.hasValue());
                this.response.appendInt((assignment.getValue() != null) ? assignment.getValue() : 0);
                this.response.appendInt(assignment.getCreatedAt());
                this.response.appendInt(assignment.getUpdatedAt());
            }
        }

        this.response.appendInt(this.contextDefinitions.size());

        for (WiredVariableDefinitionInfo definition : this.contextDefinitions) {
            if (definition == null) {
                continue;
            }

            this.response.appendInt(definition.getItemId());
            this.response.appendString(definition.getName());
            this.response.appendBoolean(definition.hasValue());
            this.response.appendInt(definition.getAvailability());
            this.response.appendBoolean(definition.isTextConnected());
            this.response.appendBoolean(definition.isReadOnly());
        }

        Room currentRoom = (roomId > 0) ? Emulator.getGameEnvironment().getRoomManager().getRoom(roomId) : null;
        List<WiredExtraVariableFxBase> fxList = new ArrayList<>();
        if (currentRoom != null && currentRoom.getFloorItems() != null) {
            for (HabboItem item : currentRoom.getFloorItems()) {
                if (item instanceof WiredExtraVariableFxBase fx) {
                    fxList.add(fx);
                }
            }
        }

        this.response.appendInt(fxList.size());
        for (WiredExtraVariableFxBase fx : fxList) {
            this.response.appendInt(fx.getId());
            this.response.appendInt(fx.getCategoryId());
            this.response.appendInt(fx.getStyleId());
            this.response.appendInt(fx.getColorId());
            this.response.appendInt(fx.getWidthId());
            this.response.appendInt(fx.getRendererId());
            this.response.appendInt(fx.getDefaultMinValue());
            this.response.appendInt(fx.getDefaultMaxValue());
            this.response.appendInt(fx.getSegments());
            this.response.appendInt(fx.getX());
            this.response.appendInt(fx.getY());
            this.response.appendString(fx.resolveStackVariableName(currentRoom));
            this.response.appendString(fx.getIcon() != null ? fx.getIcon() : "");
            this.response.appendInt(fx.getShowMode());
            this.response.appendInt(fx.getChangeFilter());
            this.response.appendBoolean(fx.isShowOnMouseHover());
            this.response.appendInt(fx.getShowDuration());
            this.response.appendInt(fx.getSourceType());
            this.response.appendInt(fx.getVisibility());
            this.response.appendInt(parseVariableId(fx.getAudienceVariableId()));
            this.response.appendInt(fx.getAudienceVariableValue());
            this.response.appendInt(fx.getSubRendererId());
            this.response.appendInt(fx.getIconAlignment());
            this.response.appendBoolean(fx.isOverrideMinEnabled());
            this.response.appendBoolean(fx.isOverrideMaxEnabled());
            this.response.appendInt(fx.getOverrideMinTarget());
            this.response.appendInt(fx.getOverrideMaxTarget());
            this.response.appendInt(parseVariableId(fx.getOverrideMinVariableId()));
            this.response.appendInt(parseVariableId(fx.getOverrideMaxVariableId()));
        }

        List<String> achievementNames = resolveEnabledAchievements(currentRoom);
        this.response.appendInt(achievementNames.size());
        for (String achievementName : achievementNames) {
            this.response.appendString(achievementName);
        }

        return this.response;
    }

    private static List<String> resolveEnabledAchievements(Room room) {
        if (room == null || room.getFloorItems() == null) return Collections.emptyList();

        Set<String> result = new LinkedHashSet<>();
        for (HabboItem item : room.getFloorItems()) {
            if (!(item instanceof WiredExtraAchievementEnabler enabler)) continue;

            String configured = enabler.getAchievementIds();
            if (configured == null || configured.isBlank()) continue;

            for (String value : configured.split("[,\\r\\n]+")) {
                String achievementName = value.trim();
                if (!achievementName.isEmpty()
                        && Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementName) != null) {
                    result.add(achievementName);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private static int parseVariableId(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static List<WiredVariableDefinitionInfo> resolveContextDefinitions(RoomUserVariableManager.Snapshot userSnapshot, RoomFurniVariableManager.Snapshot furniSnapshot, RoomVariableManager.Snapshot roomSnapshot) {
        int roomId = 0;

        if (userSnapshot != null) {
            roomId = userSnapshot.getRoomId();
        } else if (furniSnapshot != null) {
            roomId = furniSnapshot.getRoomId();
        } else if (roomSnapshot != null) {
            roomId = roomSnapshot.getRoomId();
        }

        if (roomId <= 0) {
            return Collections.emptyList();
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        return room != null ? WiredContextVariableSupport.createDefinitionInfos(room) : Collections.emptyList();
    }
}
