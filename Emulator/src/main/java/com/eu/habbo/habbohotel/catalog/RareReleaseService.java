package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import java.sql.SQLException;
import java.time.Instant;
import com.eu.habbo.util.HotelDateTimeUtil;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RareReleaseService implements Runnable {
    private static final Logger LOGGER=LoggerFactory.getLogger(RareReleaseService.class);
    private static final AtomicReference<Map<Integer,List<Release>>> RELEASES = new AtomicReference<>(Map.of());
    private record PurchasePrice(CatalogItem item,Release release){}
    private static final ThreadLocal<PurchasePrice> purchasePrice=new ThreadLocal<>();
    public static boolean beginPurchase(CatalogItem item){
        var release=active(item);if(normalItem(item)&&RELEASES.get().containsKey(item.getId())&&release==null)return false;
        if(release!=null)purchasePrice.set(new PurchasePrice(item,release));return true;
    }
    public static void endPurchase(){purchasePrice.remove();}
    private volatile boolean running=true;
    private String lastPhase="";
    private long nextHistoryCheck;
    private static final java.util.concurrent.atomic.AtomicBoolean historyRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean historyRequested=new java.util.concurrent.atomic.AtomicBoolean();
    public static void requestHistoryBackfill(){
        historyRequested.set(true);
        if(!historyRunning.compareAndSet(false,true))return;
        Emulator.getThreading().run(()->{
            try{do{historyRequested.set(false);RareEconomyRepository.backfillCatalogHistory(Emulator.getDatabase().getDataSource());}while(historyRequested.get());}
            catch(SQLException e){LOGGER.error("Rare catalog history backfill failed; it will retry",e);}
            finally{historyRunning.set(false);if(historyRequested.get())requestHistoryBackfill();}
        });
    }
    public static Release active(int offerId,long now) {
        for(var release:RELEASES.get().getOrDefault(offerId,List.of())) if(release.active(now)) return release;
        return null;
    }
    public static Release active(CatalogItem item) {
        var pinned=purchasePrice.get();if(pinned!=null&&pinned.item()==item)return pinned.release();
        if(!normalItem(item)) return null;
        return active(item.getId(),Instant.now().getEpochSecond());
    }
    public static boolean available(CatalogItem item) {
        return !normalItem(item) || !RELEASES.get().containsKey(item.getId()) || active(item)!=null;
    }
    private static boolean normalItem(CatalogItem item) {
        var env=Emulator.getGameEnvironment();
        if(env==null || env.getCatalogManager()==null)return false;
        var page=env.getCatalogManager().getCatalogPagesMap(com.eu.habbo.habbohotel.catalog.CatalogPageType.NORMAL).get(item.getPageId());
        return page!=null&&page.getCatalogItem(item.getId())==item;
    }
    public void init() throws SQLException {
        reload();Emulator.getThreading().run(this);
        requestHistoryBackfill();
    }
    public void dispose(){running=false;}
    public static void reload() throws SQLException {
        var next=new HashMap<Integer,List<Release>>();
        try(var c=Emulator.getDatabase().getDataSource().getConnection();var s=c.prepareStatement("SELECT * FROM rare_catalog_releases ORDER BY starts_at,id");var r=s.executeQuery()) {
            while(r.next()) {
                var release=new Release(r.getInt("id"),r.getInt("catalog_offer_id"),r.getInt("base_item_id"),r.getInt("price"),r.getInt("currency_type"),r.getLong("starts_at"),r.getLong("ends_at"),r.getString("repeat_mode"),r.getBoolean("enabled"));
                next.computeIfAbsent(release.offerId(),ignored->new ArrayList<>()).add(release);
            }
        }
        next.replaceAll((key,value)->List.copyOf(value));RELEASES.set(Map.copyOf(next));
    }
    public static List<Release> forBase(int id){return RELEASES.get().values().stream().flatMap(List::stream).filter(r->r.baseId()==id).toList();}
    public static synchronized void save(int adminId,int id,int offer,int base,int price,int currency,long start,long end,String repeat,boolean enabled) throws SQLException {
        if(price<1 || price>1_000_000_000 || start<0 || end<=start || !List.of("none","weekly","monthly").contains(repeat)) throw new IllegalArgumentException("Prezzo o intervallo non valido");
        if(!repeat.equals("none") && end-start>(repeat.equals("weekly")?604800:2419200)) throw new IllegalArgumentException("La durata deve essere inferiore alla cadenza");
        if(!new RareEconomyRepository(Emulator.getDatabase().getDataSource()).currencyEnabled(currency)) throw new IllegalArgumentException("Valuta non abilitata");
        try(var c=Emulator.getDatabase().getDataSource().getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("SELECT ci.item_ids,ci.amount,b.is_rare FROM catalog_items ci JOIN items_base b ON b.id=? WHERE ci.id=? FOR UPDATE")) {
                    s.setInt(1,base);s.setInt(2,offer);try(var r=s.executeQuery()){
                        if(!r.next() || r.getInt("amount")!=1 || !r.getString("item_ids").trim().equals(Integer.toString(base)))throw new IllegalArgumentException("Seleziona un'offerta contenente un solo furni raro");
                    }
                }
                if(id>0){try(var s=c.prepareStatement("SELECT base_item_id FROM rare_catalog_releases WHERE id=? FOR UPDATE")){s.setInt(1,id);try(var r=s.executeQuery()){if(!r.next()||r.getInt(1)!=base)throw new IllegalArgumentException("Pubblicazione non trovata");}}}
                for(var existing:forBase(base)) {
                    if(existing.id()==id || !existing.enabled() || !enabled) continue;
                    if(!existing.repeat().equals("none") || !repeat.equals("none") || (start<existing.end()&&end>existing.start())) throw new IllegalArgumentException("L'offerta ha già una pubblicazione sovrapposta o ricorrente");
                }
                String sql=id>0?"UPDATE rare_catalog_releases SET catalog_offer_id=?,base_item_id=?,price=?,currency_type=?,starts_at=?,ends_at=?,repeat_mode=?,enabled=? WHERE id=?":"INSERT INTO rare_catalog_releases(catalog_offer_id,base_item_id,price,currency_type,starts_at,ends_at,repeat_mode,enabled,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,UNIX_TIMESTAMP())";
                try(var s=c.prepareStatement(sql)){s.setInt(1,offer);s.setInt(2,base);s.setInt(3,price);s.setInt(4,currency);s.setLong(5,start);s.setLong(6,end);s.setString(7,repeat);s.setBoolean(8,enabled);s.setInt(9,id>0?id:adminId);s.executeUpdate();}
                boolean activeNow=new Release(id,offer,base,price,currency,start,end,repeat,enabled).active(Instant.now().getEpochSecond());
                try(var s=c.prepareStatement("UPDATE items_base SET value_type=IF(? OR catalog_price=0,?,value_type),catalog_price=IF(? OR catalog_price=0,?,catalog_price),is_rare=1 WHERE id=?")){s.setBoolean(1,activeNow);s.setInt(2,currency);s.setBoolean(3,activeNow);s.setInt(4,price);s.setInt(5,base);s.executeUpdate();}
                c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
        reload();broadcast();requestHistoryBackfill();
    }
    private static void broadcast(){
        var env=Emulator.getGameEnvironment();if(env==null||env.getCatalogManager()==null)return;
        env.getCatalogManager().markCatalogChanged();
        if(env.getHabboManager()!=null)for(var habbo:env.getHabboManager().getOnlineHabbos().values())if(habbo.getClient()!=null)habbo.getClient().sendResponse(new CatalogUpdatedComposer());
    }
    @Override public void run(){
        if(!running)return;
        try{
            long now=Instant.now().getEpochSecond();var phases=new ArrayList<String>();
            if(now>=nextHistoryCheck){nextHistoryCheck=now+60;requestHistoryBackfill();}
            var releases = RELEASES.get();
            for(int offer:releases.keySet()){var active=active(offer,now);if(active!=null)phases.add(offer+":"+active.id()+":"+active.windowStart(now));}
            phases.sort(String::compareTo);String phase=phases.toString();
            if(!phase.equals(lastPhase)){
                try(var c=Emulator.getDatabase().getDataSource().getConnection();var s=c.prepareStatement("UPDATE items_base SET catalog_price=?,value_type=?,is_rare=1 WHERE id=?")){
                    for(int offer:releases.keySet()){var active=active(offer,now);if(active!=null){s.setInt(1,active.price());s.setInt(2,active.currency());s.setInt(3,active.baseId());s.addBatch();}}s.executeBatch();
                }
                lastPhase=phase;broadcast();
            }
        }catch(Exception e){LOGGER.error("Rare catalog schedule failed",e);}finally{if(running)Emulator.getThreading().run(this,1000);}
    }
    public record Release(int id,int offerId,int baseId,int price,int currency,long start,long end,String repeat,boolean enabled){
        public long windowStart(long now){
            if(repeat.equals("none")||now<start)return start;
            var anchor=Instant.ofEpochSecond(start).atZone(HotelDateTimeUtil.getZoneId());var current=Instant.ofEpochSecond(now).atZone(anchor.getZone());
            long n=repeat.equals("weekly")?ChronoUnit.WEEKS.between(anchor.toLocalDate(),current.toLocalDate()):ChronoUnit.MONTHS.between(java.time.YearMonth.from(anchor),java.time.YearMonth.from(current));
            n=Math.max(0,n);var candidate=repeat.equals("weekly")?anchor.plusWeeks(n):anchor.plusMonths(n);
            if(candidate.toEpochSecond()>now&&n>0)candidate=repeat.equals("weekly")?anchor.plusWeeks(n-1):anchor.plusMonths(n-1);
            return candidate.toEpochSecond();
        }
        public boolean active(long now){long s=windowStart(now);return enabled&&now>=s&&now<s+(end-start);}
    }
}
