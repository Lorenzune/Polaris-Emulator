package com.eu.habbo.habbohotel.pets.breeding;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonsterplantBreedingRequestsTest {
    @Test void requestsExpireAndCanOnlyBeClaimedOnce() {
        AtomicLong now = new AtomicLong(1000);
        var requests = new MonsterplantBreedingRequests(now::get);
        assertTrue(requests.open(10, 1, 2, 100));
        var request = requests.on(2);
        assertEquals(100, request.roomId());
        assertTrue(requests.close(request));
        assertFalse(requests.close(request));
        assertTrue(requests.open(10, 1, 2, 100));
        now.addAndGet(60_000);
        assertNull(requests.on(2));
        assertTrue(requests.open(10, 1, 2, 100));
        assertFalse(requests.close(request), "an old acknowledgement cannot consume a newer request");
    }
    @Test void aPetCannotParticipateInOverlappingRequests() {
        var requests = new MonsterplantBreedingRequests(() -> 1L);
        assertTrue(requests.open(10, 1, 2, 100));
        assertFalse(requests.open(20, 3, 2, 100));
        assertFalse(requests.open(20, 2, 3, 100));
        assertFalse(requests.open(20, 1, 3, 100));
        assertFalse(requests.open(20, 3, 1, 100));
        assertTrue(requests.open(20, 3, 4, 100));
    }
    @Test void leavingEitherOwnerCancelsOnlyTheirRequestsInThatRoom() {
        var requests = new MonsterplantBreedingRequests(() -> 1L);
        assertTrue(requests.open(10, 1, 2, 100));
        assertTrue(requests.open(30, 3, 4, 200));
        assertEquals(1, requests.cancelOwner(20, 100, id -> id == 2 ? 20 : 30).size());
        assertNull(requests.on(2));
        assertNotNull(requests.on(4));
        assertEquals(1, requests.cancelOwner(30, 200, id -> 40).size());
        assertNull(requests.on(4));
    }
    @Test void requestsAreBoundedAndCapacityIsRecoveredAfterExpiry() {
        AtomicLong now = new AtomicLong(1);
        var requests = new MonsterplantBreedingRequests(now::get);
        for (int i = 0; i < 4096; i++) assertTrue(requests.open(10, 1 + i * 2, 2 + i * 2, 100));
        assertFalse(requests.open(10, 9000, 9001, 100));
        now.addAndGet(60_000);
        assertTrue(requests.open(10, 9000, 9001, 100));
    }
}
