package com.eu.habbo.habbohotel.catalog.marketplace;
import com.eu.habbo.habbohotel.earnings.PendingCurrencyEarnings;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketPlaceCreditClaimContractTest {
    @Test void claimedProceedsGoToTheWalletRatherThanBecomingPendingAgain() {
        for(int type:new int[]{-1,0,5,4})assertFalse(PendingCurrencyEarnings.shouldCapture(
                new EconomyOperation("test",1,1,"marketplace_claim","earnings.claim.marketplace",type,100,1,"offerId=1")));
    }
}
