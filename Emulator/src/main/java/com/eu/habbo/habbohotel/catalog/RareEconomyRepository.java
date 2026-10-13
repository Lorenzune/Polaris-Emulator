package com.eu.habbo.habbohotel.catalog;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RareEconomyRepository {
    public static void backfillCatalogHistory(DataSource source) throws SQLException {
        String revision;
        try(var c=source.getConnection();var marker=c.prepareStatement("SELECT COALESCE((SELECT `value` FROM rare_system_metadata WHERE `key`='catalog_history_revision'),'0') revision,(SELECT `value` FROM rare_system_metadata WHERE `key`='catalog_history_backfilled') completed");var done=marker.executeQuery()){
            done.next();revision=done.getString("revision");if(revision.equals(done.getString("completed")))return;
        }
        var candidates=new java.util.HashSet<Integer>();
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT item_id FROM rare_emissions WHERE source='legacy_reference' AND cancelled=0");var r=s.executeQuery()){while(r.next())candidates.add(r.getInt(1));}
        if(!candidates.isEmpty()){
            try(var reader=source.getConnection();var writer=source.getConnection();var logs=reader.prepareStatement("SELECT timestamp,user_id,catalog_item_id,item_ids,cost_credits,cost_points,points_type FROM logs_shop_purchases ORDER BY id")){
                logs.setFetchSize(256);writer.setAutoCommit(false);
                try(var rows=logs.executeQuery()){
                    while(rows.next()){
                        String idsText=rows.getString("item_ids");if(idsText==null)continue;
                        var ids=new java.util.HashSet<Integer>();
                        for(String token:idsText.split("[;,]")){try{int id=Integer.parseInt(token.trim());if(id>0)ids.add(id);}catch(NumberFormatException malformed){}}
                        int credits=rows.getInt("cost_credits"),points=rows.getInt("cost_points");if(ids.isEmpty()||(credits<=0&&points<=0))continue;
                        var matching=ids.stream().filter(candidates::contains).toList();if(matching.isEmpty())continue;
                        try{
                            for(int item:matching){
                                try(var update=writer.prepareStatement("UPDATE rare_emissions SET source='catalog',issuer_id=?,issued_at=?,catalog_offer_id=? WHERE item_id=? AND source='legacy_reference' AND cancelled=0")){
                                    update.setInt(1,rows.getInt("user_id"));update.setLong(2,rows.getLong("timestamp"));update.setInt(3,rows.getInt("catalog_item_id"));update.setInt(4,item);if(update.executeUpdate()!=1)continue;
                                }
                                try(var remove=writer.prepareStatement("DELETE FROM rare_emission_values WHERE item_id=?")){remove.setInt(1,item);remove.executeUpdate();}
                                if(credits>0)historicalValue(writer,item,-1,credits,ids.size());
                                if(points>0)historicalValue(writer,item,rows.getInt("points_type"),points,ids.size());
                            }
                            writer.commit();candidates.removeAll(matching);
                        }catch(SQLException failure){writer.rollback();throw failure;}
                    }
                }
            }
        }
        try(var c=source.getConnection();var s=c.prepareStatement("INSERT INTO rare_system_metadata VALUES('catalog_history_backfilled',?) ON DUPLICATE KEY UPDATE `value`=VALUES(`value`)")){s.setString(1,revision);s.executeUpdate();}
    }
    private static void historicalValue(Connection c,int item,int currency,int total,int quantity) throws SQLException {
        var price=new java.math.BigDecimal(total).divide(new java.math.BigDecimal(quantity),6,java.math.RoundingMode.HALF_UP);
        try(var s=c.prepareStatement("INSERT IGNORE INTO rare_emission_values(item_id,currency_type,unit_value) VALUES(?,?,?)")){s.setInt(1,item);s.setInt(2,currency);s.setBigDecimal(3,price);s.executeUpdate();}
    }
    public static void cancelEmission(int id) {
        try(var c=com.eu.habbo.Emulator.getDatabase().getDataSource().getConnection();var s=c.prepareStatement("UPDATE rare_emissions SET cancelled=1 WHERE item_id=?")){s.setInt(1,id);s.executeUpdate();}
        catch(SQLException e){org.slf4j.LoggerFactory.getLogger(RareEconomyRepository.class).error("Failed to mark cancelled rare emission {}",id,e);}
    }
    private final DataSource dataSource;
    public RareEconomyRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public List<Map<String,Object>> currencies() throws SQLException {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("INSERT IGNORE INTO rare_currency_types(id,label) SELECT DISTINCT type,CONCAT('Valuta ',type) FROM users_currency WHERE type>=0")){s.executeUpdate();}
        try (var c=dataSource.getConnection(); var s=c.prepareStatement("SELECT id,label FROM rare_currency_types WHERE enabled=1 ORDER BY id"); var r=s.executeQuery()) {
            var out=new ArrayList<Map<String,Object>>();
            while(r.next()) out.add(Map.of("id",r.getInt("id"),"label",r.getString("label")));
            return out;
        }
    }
    public boolean currencyEnabled(int id) throws SQLException {
        try(var c=dataSource.getConnection(); var s=c.prepareStatement("SELECT 1 FROM rare_currency_types WHERE id=? AND enabled=1")) {
            s.setInt(1,id); try(var r=s.executeQuery()) { return r.next(); }
        }
    }
    public Map<String,Object> find(int spriteId,String type) throws SQLException {
        return findRecord("b.sprite_id=? AND b.type=?",spriteId,type);
    }
    public Map<String,Object> findByBaseId(int baseId) throws SQLException {
        return findRecord("b.id=?",baseId,null);
    }
    private Map<String,Object> findRecord(String predicate,int id,String type) throws SQLException {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT b.*,COALESCE(t.label,CONCAT('Valuta ',b.value_type)) currency_label FROM items_base b LEFT JOIN rare_currency_types t ON t.id=b.value_type WHERE "+predicate+" ORDER BY b.id LIMIT 1")) {
            s.setInt(1,id);if(type!=null)s.setString(2,type);
            try(var r=s.executeQuery()) {
                if(!r.next()) return Map.of("isRare",false,"value",0,"valueType",-1,"catalogPrice",0,"currencyName","Crediti");
                var out=new HashMap<String,Object>(); int baseId=r.getInt("id");
                out.put("value",r.getBigDecimal("catalog_price"));out.put("catalogPrice",r.getBigDecimal("catalog_price"));
                out.put("valueType",r.getInt("value_type"));out.put("currencyName",r.getString("currency_label"));out.put("isRare",r.getInt("is_rare")==1);
                if(r.getInt("is_rare")!=1 && type!=null) return out;
                out.put("catalogAverages",averages(c,baseId,false));out.put("marketplaceAverages",averages(c,baseId,true));
                publicationMetadata(c,baseId,out);
                try(var counts=c.prepareStatement("SELECT (SELECT COUNT(*) FROM rare_emissions WHERE base_item_id=? AND cancelled=0) emitted,(SELECT COUNT(*) FROM items WHERE item_id=?) circulating")) {
                    counts.setInt(1,baseId);counts.setInt(2,baseId);try(var cr=counts.executeQuery()){cr.next();out.put("emitted",cr.getLong("emitted"));out.put("circulating",cr.getLong("circulating"));}
                }
                return out;
            }
        }
    }
    private void publicationMetadata(Connection c,int baseId,Map<String,Object> out) throws SQLException {
        long now=java.time.Instant.now().getEpochSecond();
        RareReleaseService.Release selected=null;long selectedStart=0;
        for(var release:RareReleaseService.forBase(baseId)) {
            if(!release.enabled())continue;
            long start=release.windowStart(now);
            boolean preferable=selected==null || (start<=now && (selectedStart>now || start>selectedStart))
                    || (start>now && selectedStart>now && start<selectedStart);
            if(preferable){selected=release;selectedStart=start;}
        }
        if(selected!=null){
            out.put("releaseStartsAt",selectedStart);
            if(selected.active(now))out.put("releaseEndsAt",selectedStart+selected.end()-selected.start());
        }
        var environment=com.eu.habbo.Emulator.getGameEnvironment();
        if(environment==null || environment.getCatalogManager()==null)return;
        var pages=environment.getCatalogManager().getCatalogPagesMap(CatalogPageType.NORMAL);
        try(var s=c.prepareStatement("SELECT ci.id,ci.page_id,p.caption FROM catalog_items ci JOIN catalog_pages p ON p.id=ci.page_id WHERE ci.item_ids=? ORDER BY (ci.id=?) DESC,ci.id")) {
            s.setString(1,Integer.toString(baseId));s.setInt(2,selected==null?0:selected.offerId());
            try(var rows=s.executeQuery()){
                while(rows.next()){
                    var page=pages.get(rows.getInt("page_id"));if(page==null || page.getRank()!=1)continue;
                    var ancestor=page;var seen=new java.util.HashSet<Integer>();boolean publiclyVisible=true;
                    while(ancestor!=null){
                        if(!seen.add(ancestor.getId()) || !ancestor.isVisible() || !ancestor.isEnabled() || ancestor.getRank()>1){publiclyVisible=false;break;}
                        if(ancestor.getParentId()<0)break;
                        ancestor=pages.get(ancestor.getParentId());
                        if(ancestor==null)publiclyVisible=false;
                    }
                    if(publiclyVisible){out.put("catalogCategory",rows.getString("caption"));break;}
                }
            }
        }
    }
    private List<Map<String,Object>> averages(Connection c,int baseId,boolean market) throws SQLException {
        String sql=market
            ? "SELECT s.currency_type,COALESCE(t.label,CONCAT('Valuta ',s.currency_type)) label,AVG(s.unit_price) average,COUNT(*) quantity,0 generated,0 legacy FROM rare_market_sales s LEFT JOIN rare_currency_types t ON t.id=s.currency_type WHERE s.base_item_id=? GROUP BY s.currency_type,t.label ORDER BY s.currency_type"
            : "SELECT v.currency_type,COALESCE(t.label,CONCAT('Valuta ',v.currency_type)) label,AVG(v.unit_value) average,COUNT(*) quantity,SUM(e.source='generated') generated,SUM(e.source='legacy_reference') legacy FROM rare_emissions e JOIN rare_emission_values v ON v.item_id=e.item_id LEFT JOIN rare_currency_types t ON t.id=v.currency_type WHERE e.base_item_id=? AND e.cancelled=0 AND v.unit_value IS NOT NULL GROUP BY v.currency_type,t.label ORDER BY v.currency_type";
        try(var s=c.prepareStatement(sql)){s.setInt(1,baseId);try(var r=s.executeQuery()){
            var out=new ArrayList<Map<String,Object>>();while(r.next())out.add(Map.of("currencyType",r.getInt("currency_type"),"currencyName",r.getString("label"),"average",r.getBigDecimal("average"),"quantity",r.getLong("quantity"),"generated",r.getLong("generated"),"legacy",r.getLong("legacy")));return out;
        }}
    }
}
