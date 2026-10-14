package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.toolbar.ToolbarConfiguration;
import com.eu.habbo.habbohotel.toolbar.ToolbarConfigurationStore;
import com.eu.habbo.habbohotel.toolbar.ToolbarConfigurationSubscribers;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import com.eu.habbo.messages.outgoing.housekeeping.ToolbarConfigurationComposer;
import org.slf4j.LoggerFactory;

public class HousekeepingSaveToolbarEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.TOOLBAR;
    }

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) return;
        int revision = this.packet.readInt();
        String json = this.packet.readString();
        ToolbarConfiguration configuration;
        try {
            configuration = ToolbarConfiguration.parse(json);
        } catch (RuntimeException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(
                    "hotel.toolbar.save", false, 0, "housekeeping.error.invalid_input"));
            return;
        }
        var actor = this.client.getHabbo().getHabboInfo();
        try {
            if (!ToolbarConfigurationStore.save(revision, configuration, actor.getId())) {
                this.client.sendResponse(new HousekeepingActionResultComposer(
                        "hotel.toolbar.save", false, 0, "housekeeping.toolbar.conflict"));
                this.client.sendResponse(new ToolbarConfigurationComposer(ToolbarConfigurationStore.load()));
                return;
            }
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(HousekeepingSaveToolbarEvent.class)
                    .error("Could not save toolbar configuration", e);
            this.client.sendResponse(new HousekeepingActionResultComposer(
                    "hotel.toolbar.save", false, 0, "housekeeping.toolbar.save_failed"));
            return;
        }
        HousekeepingAuditLog.log(
                actor.getId(),
                actor.getUsername(),
                "hotel.toolbar.save",
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "revision=" + (revision + 1),
                actor.getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer("hotel.toolbar.save", true, 0, ""));
        ToolbarConfigurationSubscribers.broadcast(new ToolbarConfigurationStore.State(revision + 1, configuration));
    }
}
