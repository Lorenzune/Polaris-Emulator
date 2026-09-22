package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildCustomBadgeRequestManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildCustomBadgeReviewResultComposer;
import com.eu.habbo.messages.outgoing.guilds.GuildCustomBadgeSavedComposer;

public class GuildCustomBadgeReviewEvent extends MessageHandler {
    @Override
    public void handle() {
        Habbo reviewer = this.client.getHabbo();
        if (!GuildCustomBadgeRequestsEvent.isReviewer(reviewer)) return;
        if (this.packet.getBuffer().readableBytes() < 5) return;

        int requestId = this.packet.readInt();
        boolean approve = this.packet.readBoolean();
        if (requestId <= 0) return;

        GuildCustomBadgeRequestManager.ReviewResult result = GuildCustomBadgeRequestManager.review(
                requestId, reviewer.getHabboInfo().getId(), approve);
        if (result == null) {
            this.client.sendResponse(new GuildCustomBadgeReviewResultComposer(
                    requestId, false, approve, "Richiesta non più disponibile."));
            return;
        }

        Habbo requester = Emulator.getGameServer().getGameClientManager().getHabbo(result.request.requesterId);
        if (result.approved) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(result.request.guildId);
            if (guild != null) {
                GuildUploadCustomBadgeEvent.applyBadge(
                        guild, result.request.badgeCode, requester != null ? requester : reviewer);
            }
        }

        if (requester != null && requester.getClient() != null) {
            requester
                    .getClient()
                    .sendResponse(new GuildCustomBadgeSavedComposer(
                            result.request.guildId,
                            result.request.badgeCode,
                            result.approved ? "approved" : "rejected"));
        }

        this.client.sendResponse(new GuildCustomBadgeReviewResultComposer(
                requestId, true, result.approved, result.approved ? "Stemma approvato." : "Stemma rifiutato."));
    }
}
