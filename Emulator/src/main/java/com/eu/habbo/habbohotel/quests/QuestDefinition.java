package com.eu.habbo.habbohotel.quests;

import java.util.List;

public class QuestDefinition {
    public String code = "", title = "", description = "", chainCode = "", imageUrl = "";
    public long expiresAt;

    public boolean available(long now) {
        return enabled && (expiresAt == 0 || expiresAt > now);
    }

    public boolean chain, enabled = true;
    public int position, roomId, target = 1;
    public Reward reward = new Reward();

    public static class Reward {
        public String kind = "none", badge = "", itemCode = "";
        public int currencyType = -1, amount = 1;
    }

    public void validate() {
        if (expiresAt < 0 || imageUrl == null || imageUrl.length() > 2048)
            throw new IllegalArgumentException("Scadenza o immagine non valida");
        if (!imageUrl.isEmpty()) {
            try {
                var uri = java.net.URI.create(imageUrl);
                if (!(imageUrl.startsWith("/") && !imageUrl.startsWith("//"))
                        && !(("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                                && uri.getHost() != null
                                && uri.getUserInfo() == null)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Usa un URL HTTP/HTTPS o un percorso locale per l'immagine");
            }
        }
        if (code == null
                || title == null
                || description == null
                || chainCode == null
                || !code.matches("[A-Za-z0-9_.-]{1,100}")
                || title.isBlank()
                || title.length() > 100
                || description.length() > 1000
                || (!chainCode.isEmpty() && !chainCode.matches("[A-Za-z0-9_.-]{1,100}"))
                || code.equals(chainCode)
                || roomId < 0
                || target < 1
                || target > 100000
                || position < 0
                || position > 1000
                || reward == null) throw new IllegalArgumentException("Definizione missione non valida");
        if (reward.kind == null
                || reward.badge == null
                || reward.itemCode == null
                || !List.of("none", "currency", "item", "badge").contains(reward.kind)
                || reward.amount < 1
                || reward.amount > 1000
                || (reward.kind.equals("currency") && !List.of(-1, 0, 5).contains(reward.currencyType))
                || (reward.kind.equals("badge") && !reward.badge.matches("[A-Za-z0-9_-]{1,40}"))
                || (reward.kind.equals("item") && !reward.itemCode.matches("[A-Za-z0-9_*.-]{1,100}")))
            throw new IllegalArgumentException("Ricompensa non valida");
    }
}
