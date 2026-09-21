package com.eu.habbo.messages.incoming.rooms.youtube;

/** Dedicated incoming handler for the independent :play Music Party channel. */
public class MusicPartyRoomPlayEvent extends YouTubeRoomPlayEvent {
    @Override
    protected boolean isMusicParty() {
        return true;
    }
}
