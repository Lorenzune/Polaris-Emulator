package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.RareEconomyRepository;
import com.eu.habbo.habbohotel.catalog.RareReleaseService;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.Map;

public class RareEconomyCommandEvent extends MessageHandler {
    @Override public int getRatelimit(){return 100;}
    @Override public void handle() throws Exception {
        String action=this.packet.readString();String raw=this.packet.readString();
        int baseId=0;Object data=Map.of();boolean success=true;String message="";String requestId="";
        try{
            if(raw.length()>16384)throw new IllegalArgumentException("Richiesta troppo grande");
            var json=JsonParser.parseString(raw).getAsJsonObject();baseId=json.has("baseId")?json.get("baseId").getAsInt():0;
            requestId=json.has("requestId")?json.get("requestId").getAsString():"";
            if(requestId.length()>64)throw new IllegalArgumentException("ID richiesta non valido");
            if(action.startsWith("admin-"))data=new com.eu.habbo.habbohotel.catalog.RareManagerService(Emulator.getDatabase().getDataSource()).execute(this.client.getHabbo(),action,json);
            else if(action.equals("config"))data=Map.of("currencies",new RareEconomyRepository(Emulator.getDatabase().getDataSource()).currencies(),"defaultCurrency",com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace.legacyCurrency());
            else{
                if(!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI))throw new IllegalArgumentException("Permesso mancante");
                if(action.equals("save"))RareReleaseService.save(this.client.getHabbo().getHabboInfo().getId(),json.get("id").getAsInt(),json.get("offerId").getAsInt(),baseId,json.get("price").getAsInt(),json.get("currency").getAsInt(),json.get("start").getAsLong(),json.get("end").getAsLong(),json.get("repeat").getAsString(),json.get("enabled").getAsBoolean());
                else if(!action.equals("list"))throw new IllegalArgumentException("Azione non valida");
                data=Map.of("releases",RareReleaseService.forBase(baseId),"serverTime",java.time.Instant.now().getEpochSecond(),"timeZone",com.eu.habbo.util.HotelDateTimeUtil.getTimezoneId());
            }
        }catch(Exception e){success=false;message=e instanceof IllegalArgumentException || e instanceof SecurityException?e.getMessage():"Operazione non riuscita";}
        String jsonResponse=new Gson().toJson(Map.of("action",action,"baseId",baseId,"success",success,"message",message,"requestId",requestId,"data",data));
        // Habbo strings have a 16-bit byte length. Large category lists use optional
        // chunks on this admin action only; existing economy responses stay unchanged.
        if(action.startsWith("admin-") && jsonResponse.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000) {
            var parts=splitResponse(jsonResponse);
            for(int index=0;index<parts.size();index++)send(new Gson().toJson(Map.of("action",action,"baseId",baseId,
                "success",success,"message",message,"requestId",requestId,"data",Map.of(),
                "chunk",Map.of("index",index,"total",parts.size(),"payload",parts.get(index)))));
        }else send(jsonResponse);
    }
    static java.util.List<String> splitResponse(String value) {
        var out=new java.util.ArrayList<String>();
        for(int start=0;start<value.length();) {
            int end=Math.min(value.length(),start+8000);
            if(end<value.length() && Character.isHighSurrogate(value.charAt(end-1)))end--;
            out.add(value.substring(start,end));start=end;
        }
        return out;
    }
    private void send(String json) {
        this.client.sendResponse(new MessageComposer(){@Override protected ServerMessage composeInternal(){this.response.init(10411);this.response.appendString(json);return this.response;}});
    }
}
