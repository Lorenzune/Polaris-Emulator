package com.eu.habbo.habbohotel.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import org.junit.jupiter.api.Test;

class GuildChatBuddyTest {
    @Test
    void guildBuddyIdsStayInTheirVirtualNamespace() {
        int guildId = 42;
        int buddyId = GuildChatBuddy.getBuddyId(guildId);

        assertTrue(GuildChatBuddy.isGuildBuddy(buddyId));
        assertTrue(GuildChatBuddy.isGuildBuddy(GuildChatBuddy.GROUP_OFFSET));
        assertFalse(GuildChatBuddy.isGuildBuddy(GuildChatBuddy.GROUP_OFFSET + 1));
        assertEquals(guildId, GuildChatBuddy.getGuildId(buddyId));
    }

    @Test
    void onlyCurrentGuildMembersCanUseGuildChat() {
        for (GuildRank rank : new GuildRank[] {GuildRank.OWNER, GuildRank.ADMIN, GuildRank.MEMBER}) {
            assertTrue(GuildChatBuddy.isChatMember(member(rank)));
        }

        assertFalse(GuildChatBuddy.isChatMember(member(GuildRank.REQUESTED)));
        assertFalse(GuildChatBuddy.isChatMember(member(GuildRank.DELETED)));
        assertFalse(GuildChatBuddy.isChatMember(null));
    }

    private GuildMember member(GuildRank rank) {
        return new GuildMember(1, "Test", "", 0, rank.ordinal());
    }
}
