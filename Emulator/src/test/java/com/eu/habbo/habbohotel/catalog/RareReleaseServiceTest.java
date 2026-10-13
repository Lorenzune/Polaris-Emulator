package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import com.eu.habbo.util.HotelDateTimeUtil;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RareReleaseServiceTest {
    private MockedStatic<HotelDateTimeUtil> hotelTime;
    @BeforeEach void configureHotelZone(){hotelTime=mockStatic(HotelDateTimeUtil.class);hotelTime.when(HotelDateTimeUtil::getZoneId).thenReturn(ZoneId.of("Europe/Rome"));}
    @AfterEach void closeHotelZone(){hotelTime.close();}
    private long time(String text){return ZonedDateTime.parse(text+"[Europe/Rome]").toEpochSecond();}
    @Test void oneTimeReleaseIsHiddenOutsideItsWindow(){
        var r=new RareReleaseService.Release(1,2,3,100,5,1000,2000,"none",true);
        assertFalse(r.active(999));assertTrue(r.active(1000));assertTrue(r.active(1999));assertFalse(r.active(2000));
        assertFalse(new RareReleaseService.Release(1,2,3,100,5,1000,2000,"none",false).active(1500));
    }
    @Test void monthlyScheduleUsesCalendarMonthsAndClampsMonthEnd(){
        long start=time("2026-01-31T10:00:00+01:00");
        var r=new RareReleaseService.Release(1,2,3,100,5,start,start+86400*2,"monthly",true);
        assertTrue(r.active(time("2026-02-28T10:00:00+01:00")));
        assertFalse(r.active(time("2026-02-28T09:59:59+01:00")));
        assertTrue(r.active(time("2026-03-31T11:00:00+02:00")));
        assertFalse(r.active(time("2026-03-15T11:00:00+01:00")));
    }
    @Test void weeklySchedulePreservesLocalStartTimeAcrossDaylightSaving(){
        long start=time("2026-03-22T10:00:00+01:00");
        var r=new RareReleaseService.Release(1,2,3,100,5,start,start+3600,"weekly",true);
        assertTrue(r.active(time("2026-03-29T10:00:00+02:00")));
        assertFalse(r.active(time("2026-03-29T09:59:59+02:00")));
    }
    @Test void recurrenceUsesTheSameConfiguredHotelZoneAsInternalVariables(){
        hotelTime.when(HotelDateTimeUtil::getZoneId).thenReturn(ZoneId.of("America/New_York"));
        long start=java.time.Instant.parse("2026-03-01T15:00:00Z").getEpochSecond();
        var r=new RareReleaseService.Release(1,2,3,100,5,start,start+3600,"weekly",true);
        // New York enters daylight saving on March 8, earlier than Rome.
        long next=java.time.Instant.parse("2026-03-08T14:00:00Z").getEpochSecond();
        assertEquals(next,r.windowStart(next));assertTrue(r.active(next));assertFalse(r.active(next+3600));
    }
}
