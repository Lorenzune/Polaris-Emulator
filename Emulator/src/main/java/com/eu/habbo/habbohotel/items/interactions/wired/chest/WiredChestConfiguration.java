package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Optional configuration envelope: old clients and old saved boxes retain their integer contract. */
public final class WiredChestConfiguration {
    public int version = 1;
    public int legacyBaseItemId;
    public Integer conditionQuantifier = 0;
    public int referenceSource;
    public List<Integer> referenceIds = new ArrayList<>();
    public int referenceUserSource;
    public int amountOption;
    public int amountTarget = -20;
    public int variableId;
    public String variableToken = "";
    public int chestSource = 100;
    public int typeSource = 100;
    public List<Integer> typeIds = new ArrayList<>();
    public int rewardingMode;
    public int iterationMode;
    public int currencyType = -1;
    public int earningsCategory = 11;
    public int scanningMode;
    public boolean showByDefault;
    public String popup = "";

    public static WiredChestConfiguration read(WiredSettings settings) {
        String value = settings.getStringParam();
        if (value == null || !value.startsWith("{")) return null;
        try {
            WiredChestConfiguration config = WiredManager.getGson().fromJson(value, WiredChestConfiguration.class);
            if (config == null || config.version != 1) return null;
            int[] params = settings.getIntParams();
            if (params.length == 4) {
                config.amountOption = params[1];
                config.amountTarget = params[2];
            }
            if (params.length >= 6) {
                config.rewardingMode = params[0];
                config.amountOption = params[2];
                config.amountTarget = params[3];
                config.showByDefault = params[4] == 1;
            }
            if (params.length == 1) config.scanningMode = params[0];
            if (settings.getVariableIds() != null && settings.getVariableIds().length > 0)
                config.variableId = settings.getVariableIds()[0];
            if (settings.getFurniIds2() != null && settings.getFurniIds2().length > 0)
                config.typeIds = Arrays.stream(settings.getFurniIds2()).boxed().toList();
            int[] sources = settings.getFurniSourceTypes();
            if (sources != null && sources.length > 0) {
                config.chestSource = sources[sources.length > 1 ? 1 : 0];
                if (sources.length > 1) config.typeSource = sources[0];
            }
            config.amountOption = config.amountOption == 1 ? 1 : 0;
            config.amountTarget = config.amountTarget == -10 || config.amountTarget == 0 || config.amountTarget == 1
                    ? config.amountTarget
                    : -20;
            if (config.conditionQuantifier != null)
                config.conditionQuantifier = config.conditionQuantifier == 1 ? 1 : 0;
            config.referenceSource = source(config.referenceSource);
            config.rewardingMode = config.rewardingMode == 1 ? 1 : 0;
            config.iterationMode = Math.max(0, Math.min(2, config.iterationMode));
            config.scanningMode = config.scanningMode == 1 ? 1 : 0;
            config.chestSource = source(config.chestSource);
            config.typeSource = source(config.typeSource);
            if (config.currencyType != -1 && config.currencyType != 0 && config.currencyType != 5)
                config.currencyType = -1;
            if (config.typeIds == null) config.typeIds = new ArrayList<>();
            config.typeIds = config.typeIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .distinct()
                    .limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                    .toList();
            if (config.referenceIds == null) config.referenceIds = new ArrayList<>();
            config.referenceIds = config.referenceIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .distinct()
                    .limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                    .toList();
            if (config.popup == null) config.popup = "";
            config.popup = config.popup.substring(0, Math.min(200, config.popup.length()));
            return config;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int source(int value) {
        return value == 0 || value == 200 || value == 201 ? value : 100;
    }

    public Integer amount(WiredContext ctx, int constant) {
        if (amountOption == 0) return constant;
        if (ctx == null || ctx.room() == null) return null;
        String internal =
                variableToken != null && variableToken.startsWith("internal:") ? variableToken.substring(9) : null;
        if (variableId <= 0 && internal == null) return null;
        if (amountTarget == 0) {
            var references = items(ctx, referenceIds, referenceSource);
            if (references.isEmpty()) return null;
            if (internal != null)
                return WiredInternalVariableSupport.readFurniValue(ctx.room(), references.getFirst(), internal);
            var manager = ctx.room().getFurniVariableManager();
            int id = references.getFirst().getId();
            return manager.hasVariable(id, variableId) ? manager.getCurrentValue(id, variableId) : null;
        }
        if (amountTarget == 1) {
            var references = WiredSourceUtil.resolveUsers(ctx, referenceUserSource);
            if (references.isEmpty()) return null;
            Habbo user = ctx.room().getHabbo(references.getFirst());
            if (user == null) return null;
            if (internal != null)
                return WiredInternalVariableSupport.readUserValue(ctx.room(), references.getFirst(), internal);
            var manager = ctx.room().getUserVariableManager();
            int id = user.getHabboInfo().getId();
            return manager.hasVariable(id, variableId) ? manager.getCurrentValue(id, variableId) : null;
        }
        if (amountTarget == -20)
            return internal != null
                    ? WiredInternalVariableSupport.readContextValue(ctx, internal)
                    : WiredContextVariableSupport.getCurrentValue(ctx, variableId);
        if (internal != null) return WiredInternalVariableSupport.readRoomValue(ctx.room(), internal);
        var manager = ctx.room().getRoomVariableManager();
        return manager.getDefinitionInfo(variableId) == null ? null : manager.getCurrentValue(variableId);
    }

    public List<HabboItem> items(WiredContext ctx, Collection<Integer> ids, int source) {
        List<HabboItem> selected = new ArrayList<>();
        if (ctx == null || ctx.room() == null) return selected;
        for (Integer id : ids) {
            HabboItem item = ctx.room().getHabboItem(id);
            if (item != null) selected.add(item);
        }
        return WiredSourceUtil.resolveItems(ctx, source, selected);
    }

    public Set<Integer> itemTypes(WiredContext ctx) {
        Set<Integer> result = new HashSet<>();
        for (HabboItem item : items(ctx, typeIds, typeSource))
            result.add(item.getBaseItem().getId());
        if (typeIds.isEmpty() && typeSource == 100 && legacyBaseItemId > 0) result.add(legacyBaseItemId);
        return result;
    }

    public void appendSettings(com.eu.habbo.messages.ServerMessage message, boolean twoSelections, Integer userSource) {
        message.appendInt(com.eu.habbo.habbohotel.items.interactions.InteractionWired.WIRED_EXTENDED_SETTINGS_MARKER);
        message.appendInt(twoSelections ? 2 : 1);
        if (twoSelections) message.appendInt(typeSource);
        message.appendInt(chestSource);
        message.appendInt(userSource == null ? 0 : 1);
        if (userSource != null) message.appendInt(userSource);
        message.appendInt(variableId <= 0 ? 0 : 1);
        if (variableId > 0) message.appendInt(variableId);
        message.appendInt(typeIds.size());
        for (int id : typeIds) message.appendInt(id);
    }

    public void notifyReward(Habbo user, int given) {
        if (user == null || user.getClient() == null) return;
        if (given <= 0) {
            ChestNotifications.send(user, "rejected", "Non è stato possibile consegnare la ricompensa dallo scrigno.");
            return;
        }
        String message = popup == null || popup.isBlank() ? "Hai ricevuto una ricompensa dallo scrigno." : popup;
        user.getClient()
                .sendResponse(new com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer(
                        "wired.reward",
                        Map.of(
                                "message",
                                message,
                                "title",
                                "Ricompensa ricevuta",
                                "openByDefault",
                                showByDefault ? "1" : "0")));
    }
}
