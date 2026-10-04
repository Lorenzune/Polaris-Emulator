package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Builds payment and reward terms from selected furniture types, constants, variables and selectors. */
public class InteractionWiredCustomContract extends InteractionWiredContract {
    public static final int CODE = 113;

    public InteractionWiredCustomContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredCustomContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int contractCode() {
        return CODE;
    }
}
