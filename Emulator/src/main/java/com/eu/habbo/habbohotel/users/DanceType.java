package com.eu.habbo.habbohotel.users;

public enum DanceType {
    NONE(0),
    HAB_HOP(1),
    POGO_MOGO(2),
    DUCK_FUNK(3),
    THE_ROLLIE(4),
    CLAP(5),
    JAMAL(6),
    ONE_D(7),
    JOHN_CENA(8),
    WAVE(9),
    SLIDE(10),
    SHOULDERS(11),
    BOUNCE(12),
    SWING(13),
    FLEX(14),
    CLAP_UP(15),
    MACARENA(16);

    private final int type;

    DanceType(int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }
}
