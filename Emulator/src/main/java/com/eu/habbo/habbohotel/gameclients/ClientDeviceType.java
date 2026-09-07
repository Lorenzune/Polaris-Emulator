package com.eu.habbo.habbohotel.gameclients;

public enum ClientDeviceType {
    PC,
    TABLET,
    MOBILE;

    public static ClientDeviceType fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return PC;
        }

        String ua = userAgent.toLowerCase();

        // Tablets: iPad, tablet keywords, Android without "mobile"
        if (ua.contains("ipad")
                || ua.contains("tablet")
                || ua.contains("playbook")
                || ua.contains("silk")
                || ua.contains("kindle")
                || (ua.contains("android") && !ua.contains("mobile"))) {
            return TABLET;
        }

        // Mobile: Phones (iPhone, Android with mobile, etc.)
        if (ua.contains("mobile")
                || ua.contains("iphone")
                || ua.contains("ipod")
                || ua.contains("blackberry")
                || ua.contains("iemobile")
                || ua.contains("opera mini")
                || ua.contains("opera mobi")
                || ua.contains("webos")
                || ua.contains("windows phone")) {
            return MOBILE;
        }

        // Default to PC / Desktop (Windows, Macintosh, Linux, Electron, etc.)
        return PC;
    }
}
