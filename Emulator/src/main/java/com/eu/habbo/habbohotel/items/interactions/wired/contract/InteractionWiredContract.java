package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractTerm;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Stores chest-backed payment alternatives and reward terms. The five-integer asset format and legacy currency terms both round-trip. */
public abstract class InteractionWiredContract extends InteractionWiredExtra {
    public static final int DIR_PAY = 0;
    public static final int DIR_RECEIVE = 1;

    private static final int MAX_TERMS = 20;

    protected final List<Term> terms = new ArrayList<>();
    private ContractConfiguration configuration = new ContractConfiguration();

    protected final List<Integer> chestIds = new ArrayList<>();

    protected InteractionWiredContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** The client {@code WiredActionLayoutCode} for this contract's dialog (110-113). */
    protected abstract int contractCode();

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public List<Term> getTerms() {
        return this.terms;
    }

    public List<Integer> getChestIds() {
        return this.chestIds;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        ContractConfiguration next = ContractConfiguration.read(settings.getStringParam());

        if (this instanceof InteractionWiredCustomContract && next.customNative && params.length == 10) {
            next.paymentEnabled = params[0] == 1; next.paymentKind = params[1] == 1 ? 1 : 0;
            next.payment.amountOption = params[2] == 1 ? 1 : 0; next.paymentAmount = Math.max(1, Math.min(100000, params[3])); next.payment.amountTarget = params[4];
            next.rewardEnabled = params[5] == 1; next.rewardKind = params[6] == 1 ? 1 : 0;
            next.reward.amountOption = params[7] == 1 ? 1 : 0; next.rewardAmount = Math.max(1, Math.min(100000, params[8])); next.reward.amountTarget = params[9];
        }
        List<ContractTerm> parsed = this instanceof InteractionWiredCustomContract && next.customNative ? java.util.List.of() : ContractTerm.parse(params, settings.getStringParam());
        if (parsed.size() > MAX_TERMS) return false;
        for (ContractTerm term : parsed) {
            if (term.amount > (term.isFurni() ? 500 : 100000)) return false;
            if (this instanceof InteractionWiredContractPayment && term.direction != ContractTerm.DIR_PAY) return false;
            if (this instanceof InteractionWiredContractReward && term.direction != ContractTerm.DIR_RECEIVE) return false;
            if (term.isCurrency() && term.currencyType != -1 && term.currencyType != 0 && term.currencyType != 5) return false;
            if (term.isFurni() && term.baseItemId <= 0) return false;
        }
        List<Term> mapped = new ArrayList<>();
        for (int index = 0; index < parsed.size(); index++) {
            ContractTerm term = parsed.get(index);
            String code = index < next.furniCodes.size() ? next.furniCodes.get(index) : "";
            if (term.isFurni() && code != null && !code.isBlank()) {
                var item = com.eu.habbo.Emulator.getGameEnvironment().getItemManager().getItem(code);
                if (item == null || !item.allowTrade() || (item.getType() == com.eu.habbo.habbohotel.items.FurnitureType.WALL) != term.wallItem) return false;
                term = ContractTerm.furni(term.direction, term.wallItem, item.getId(), term.legacyPosterId, term.amount);
            }
            mapped.add(new Term(term));
        }
        this.configuration = next;
        this.terms.clear(); this.terms.addAll(mapped);
        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.chestIds.add(id);
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.terms, this.chestIds, this.configuration));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.configuration = data.configuration == null ? new ContractConfiguration() : data.configuration;
        if (data.terms != null) {
            for (Term t : data.terms) {
                if (t != null && t.amount > 0) this.terms.add(t);
            }
        }
        if (data.chestIds != null) this.chestIds.addAll(data.chestIds);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) message.appendInt(id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.configuration));
        int[] params = this instanceof InteractionWiredCustomContract && configuration.customNative
            ? new int[] {configuration.paymentEnabled ? 1 : 0, configuration.paymentKind, configuration.payment.amountOption, configuration.paymentAmount, configuration.payment.amountTarget, configuration.rewardEnabled ? 1 : 0, configuration.rewardKind, configuration.reward.amountOption, configuration.rewardAmount, configuration.reward.amountTarget}
            : ContractTerm.serialize(this.getAssetTerms());
        message.appendInt(params.length);
        for (int param : params) message.appendInt(param);
        message.appendInt(0);
        message.appendInt(this.contractCode());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.terms.clear();
        this.chestIds.clear();
        this.configuration = new ContractConfiguration();
    }

    public ContractConfiguration getConfiguration() { return configuration; }

    public List<ContractTerm> getAssetTerms() {
        return terms.stream().map(t -> new ContractTerm(t.direction, t.kind, t.currencyType, t.wallItem, t.baseItemId, t.legacyPosterId, t.amount)).toList();
    }
    /** Legacy plugin interface, with additive furniture fields. */
    public static class Term {
        public int direction, currencyType, amount, kind, baseItemId;
        public boolean wallItem;
        public String legacyPosterId = "";
        public Term() {}
        public Term(int direction, int currencyType, int amount) { this.direction = direction; this.currencyType = currencyType; this.amount = amount; }
        public Term(ContractTerm term) {
            this(term.direction, term.currencyType, term.amount);
            kind = term.kind; wallItem = term.wallItem; baseItemId = term.baseItemId; legacyPosterId = term.legacyPosterId;
        }
    }

    public InteractionWiredChest resolveLinkedChest(Room room) {
        for (int id : chestIds) {
            var selected = room.getHabboItem(id);
            if (selected instanceof InteractionWiredChest chest) return chest;
        }
        return null;
    }

    static class JsonData {
        List<Term> terms;
        List<Integer> chestIds;
        ContractConfiguration configuration;

        JsonData() {
        }

        JsonData(List<Term> terms, List<Integer> chestIds, ContractConfiguration configuration) {
            this.terms = terms;
            this.chestIds = chestIds;
            this.configuration = configuration;
        }
    }
}
