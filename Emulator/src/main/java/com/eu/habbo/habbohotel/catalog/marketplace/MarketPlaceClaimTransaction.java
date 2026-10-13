package com.eu.habbo.habbohotel.catalog.marketplace;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;

public final class MarketPlaceClaimTransaction {
    private MarketPlaceClaimTransaction() {}
    public record Claim(int id,int currency,int price){}
    public record Result(List<Integer> offerIds,Map<Integer,EconomyMutationResult> wallets,Map<Integer,Integer> totals){}
    @FunctionalInterface public interface Approval { boolean approve(Map<Integer,Integer> totals); }
    public static Result commit(DataSource source,int sellerId,int selectedCurrency,Approval approval) throws SQLException {
        if(sellerId<=0||selectedCurrency< -2)throw new IllegalArgumentException("Invalid marketplace claim");
        try(var c=source.getConnection()){
            c.setAutoCommit(false);
            try{
                var claims=new ArrayList<Claim>();var totals=new TreeMap<Integer,Integer>();
                String sql="SELECT id,currency_type,price FROM marketplace_items WHERE user_id=? AND state=2"+(selectedCurrency==-2?"":" AND currency_type=?")+" ORDER BY id FOR UPDATE";
                try(var s=c.prepareStatement(sql)){s.setInt(1,sellerId);if(selectedCurrency!=-2)s.setInt(2,selectedCurrency);try(var r=s.executeQuery()){
                    while(r.next()){var claim=new Claim(r.getInt("id"),r.getInt("currency_type"),r.getInt("price"));if(claim.price()<1)throw new SQLException("Invalid sold price");claims.add(claim);totals.merge(claim.currency(),claim.price(),Math::addExact);}
                }}
                if(!approval.approve(Map.copyOf(totals))){c.rollback();return new Result(List.of(),Map.of(),Map.of());}
                var wallets=new TreeMap<Integer,EconomyMutationResult>();var ids=new ArrayList<Integer>();
                for(var claim:claims){
                    var wallet=EconomyLedger.apply(c,new EconomyOperation("marketplace:claim:"+claim.id()+":seller:"+sellerId,sellerId,sellerId,"marketplace_claim","earnings.claim.marketplace",claim.currency(),claim.price(),claim.id(),"offerId="+claim.id()));
                    try(var s=c.prepareStatement("UPDATE marketplace_items SET user_id=-1 WHERE id=? AND user_id=? AND state=2 AND currency_type=?")){s.setInt(1,claim.id());s.setInt(2,sellerId);s.setInt(3,claim.currency());if(s.executeUpdate()!=1)throw new SQLException("Offer claim changed concurrently");}
                    wallets.put(claim.currency(),wallet);ids.add(claim.id());
                }
                c.commit();return new Result(List.copyOf(ids),Map.copyOf(wallets),Map.copyOf(totals));
            }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
        }
    }
}
