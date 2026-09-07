package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.ClientDeviceType;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.concurrent.TimeUnit;

public class OnlineCommand extends Command {
    public OnlineCommand() {
        super(null, new String[] {"online"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (gameClient == null || gameClient.getHabbo() == null) {
            return false;
        }

        int seconds = Emulator.getOnlineTime();
        int day = (int) TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - (day * 24L);
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

        int totalOnline = 0;
        int pcCount = 0;
        int tabletCount = 0;
        int mobileCount = 0;

        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo == null || !habbo.isOnline()) {
                continue;
            }
            totalOnline++;
            GameClient client = habbo.getClient();
            if (client != null) {
                ClientDeviceType deviceType = client.getDeviceType();
                if (deviceType == ClientDeviceType.MOBILE) {
                    mobileCount++;
                } else if (deviceType == ClientDeviceType.TABLET) {
                    tabletCount++;
                } else {
                    pcCount++;
                }
            } else {
                pcCount++;
            }
        }

        StringBuilder message = new StringBuilder();
        message.append("<b>Utenti Online</b>\r\r");
        message.append("<b>Tempo di attività:</b> ")
                .append(day)
                .append(day == 1 ? " giorno, " : " giorni, ")
                .append(hours)
                .append(hours == 1 ? " ora, " : " ore, ")
                .append(minute)
                .append(minute == 1 ? " minuto e " : " minuti e ")
                .append(second)
                .append(second == 1 ? " secondo" : " secondi")
                .append("\r\r");
        message.append("<b>Online totali:</b> ").append(totalOnline).append("\r");
        message.append("- <b>PC / Desktop:</b> ").append(pcCount).append("\r");
        message.append("- <b>Tablet:</b> ").append(tabletCount).append("\r");
        message.append("- <b>Mobile:</b> ").append(mobileCount);

        gameClient.getHabbo().alert(message.toString());
        return true;
    }
}
