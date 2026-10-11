package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.DefaultWiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.util.List;

/** Optional transaction source envelope; absent data preserves selected contracts and the actor. */
public final class WiredTransactionSources {
    public int version = 1;
    public int furniSource = 100;
    public int userSource;

    public static WiredTransactionSources read(String value) {
        WiredTransactionSources result = new WiredTransactionSources();
        if (value != null && value.startsWith("{")) {
            try {
                var parsed = WiredManager.getGson().fromJson(value, WiredTransactionSources.class);
                if (parsed != null && parsed.version == 1) result = parsed;
            } catch (RuntimeException ignored) {
                com.eu.habbo.WiredCompatibilityDiagnostics.record(
                        com.eu.habbo.WiredCompatibilityDiagnostics.FailurePoint.TRANSACTION_SOURCES_JSON, ignored);
            }
        }
        if (result.furniSource != 0
                && result.furniSource != 100
                && result.furniSource != 200
                && result.furniSource != 201) result.furniSource = 100;
        if (!WiredSourceUtil.isDefaultUserSource(result.userSource)) result.userSource = 0;
        return result;
    }

    public List<HabboItem> contracts(WiredContext ctx, List<Integer> ids) {
        ctx.setIncludeWiredSelectorItems(true);
        return new WiredChestConfiguration().items(ctx, ids, furniSource);
    }

    public boolean matches(HabboItem trigger, WiredEvent event, List<Integer> ids) {
        if (event.getSourceItem().isEmpty()) return false;
        if (furniSource == 100) return ids.contains(event.getSourceItem().get().getId());
        var ctx = new WiredContext(event, trigger, DefaultWiredServices.getInstance(), new WiredState(100));
        return contracts(ctx, ids).stream()
                .anyMatch(item -> item.getId() == event.getSourceItem().get().getId());
    }

    public String json() {
        return WiredManager.getGson().toJson(this);
    }
}
