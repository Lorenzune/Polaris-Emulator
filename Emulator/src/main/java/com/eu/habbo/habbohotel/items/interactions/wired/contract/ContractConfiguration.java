package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredChestConfiguration;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.ArrayList;
import java.util.List;

/** Optional extension of the established Wired save envelope. */
public final class ContractConfiguration {
    public int version = 1;
    public boolean customNative;
    public int paymentMode = 1;
    public String receiveText = "";
    public String layoutType = "generic";
    public String popup = "";
    public boolean showByDefault = true;
    public int earningsCategory = 11;
    public List<String> furniCodes = new ArrayList<>();
    public List<Integer> paymentRules = new ArrayList<>();
    public List<Integer> paymentIds = new ArrayList<>(), rewardIds = new ArrayList<>();
    public WiredChestConfiguration payment = new WiredChestConfiguration(), reward = new WiredChestConfiguration();
    public boolean paymentEnabled = false, rewardEnabled = false;
    public int paymentKind, rewardKind;
    public int paymentAmount = 1, rewardAmount = 1;

    public static ContractConfiguration read(String text) {
        if (text == null || !text.startsWith("{")) return new ContractConfiguration();
        try {
            var config = WiredManager.getGson().fromJson(text, ContractConfiguration.class);
            if (config == null || config.version != 1) return new ContractConfiguration();
            config.receiveText = trim(config.receiveText, 60);
            config.popup = trim(config.popup, 200);
            config.paymentMode = config.paymentMode == 0 ? 0 : 1;
            config.layoutType = "games".equals(config.layoutType) ? "games" : "generic";
            if (config.furniCodes == null) config.furniCodes = new ArrayList<>();
            if (config.paymentRules == null) config.paymentRules = new ArrayList<>();
            if (config.payment == null) config.payment = new WiredChestConfiguration();
            if (config.reward == null) config.reward = new WiredChestConfiguration();
            config.paymentAmount = Math.max(1, Math.min(100000, config.paymentAmount));
            config.rewardAmount = Math.max(1, Math.min(100000, config.rewardAmount));
            return config;
        } catch (RuntimeException ignored) {
            return new ContractConfiguration();
        }
    }

    private static String trim(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(max, value.length()));
    }
}
