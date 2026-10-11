package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.breeding.MonsterplantBreedingRequests;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingStartComposer;

/**
 * Breeding two monsterplants. When both belong to the same player there is nobody to ask and it
 * happens at once; when they belong to two, the owner of the second plant is asked and the plants
 * are only spent once they have said yes.
 */
public class BreedMonsterplantsEvent extends MessageHandler {
    /** The states of the official {@code BreedPets} composer. */
    private static final int STATE_START = 0;

    private static final int STATE_CANCEL = 1;
    private static final int STATE_ACCEPT = 2;

    /** The states the client reads back: 0 means "somebody is asking you". */
    private static final int ANSWER_ASKED = 0;

    private static final int ANSWER_CANCELLED = 1;
    private static final int ANSWER_ACCEPTED = 2;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!com.eu.habbo.habbohotel.pets.breeding.PetBreedingPlatform.services()
                .config()
                .get()
                .getBoolean("pet.breeding.confirmation.enabled", false)) {
            legacy();
            return;
        }
        int state = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        Pet petOne = room.getPet(this.packet.readInt());
        Pet petTwo = room.getPet(this.packet.readInt());

        if (!(petOne instanceof MonsterplantPet plantOne) || !(petTwo instanceof MonsterplantPet plantTwo)) return;

        if (plantOne == plantTwo || !plantOne.breedable() || !plantTwo.breedable()) return;

        int senderId = this.client.getHabbo().getHabboInfo().getId();

        // One of the two plants has to be the sender's, and the other has to be theirs as well or
        // open to everybody.
        boolean ownsOne = plantOne.getUserId() == senderId;
        boolean ownsTwo = plantTwo.getUserId() == senderId;

        if (!ownsOne && !ownsTwo) return;

        if (!ownsOne && !plantOne.isPubliclyBreedable()) return;

        if (!ownsTwo && !plantTwo.isPubliclyBreedable()) return;

        // The sender always speaks about their own plant first.
        MonsterplantPet mine = ownsOne ? plantOne : plantTwo;
        MonsterplantPet theirs = ownsOne ? plantTwo : plantOne;

        synchronized (room) {
            if (room.getPet(mine.getId()) != mine || room.getPet(theirs.getId()) != theirs) return;
            switch (state) {
                case STATE_START -> this.start(room, mine, theirs);
                case STATE_CANCEL -> this.cancel(room, mine, theirs);
                case STATE_ACCEPT -> this.accept(room, mine, theirs);
                default -> {}
            }
        }
    }

    private void start(Room room, MonsterplantPet mine, MonsterplantPet theirs) {
        if (theirs.getUserId() == mine.getUserId()) {
            // Both plants are the sender's: there is nobody to ask.
            mine.breed(theirs);
            return;
        }

        Habbo other = room.getHabbo(theirs.getUserId());

        if (other == null || other.getClient() == null) return;

        if (!MonsterplantBreedingRequests.getInstance()
                .open(mine.getUserId(), mine.getId(), theirs.getId(), room.getId())) return;

        other.getClient().sendResponse(new PetBreedingStartComposer(ANSWER_ASKED, theirs.getId(), mine.getId()));
        this.client.sendResponse(new PetBreedingStartComposer(3, mine.getId(), theirs.getId()));
    }

    /**
     * Either side can walk away: the one who asked withdraws the request standing on the other
     * plant, the one who was asked refuses the request standing on their own.
     */
    private void cancel(Room room, MonsterplantPet mine, MonsterplantPet theirs) {
        MonsterplantBreedingRequests requests = MonsterplantBreedingRequests.getInstance();
        int key = requests.on(mine.getId()) != null ? mine.getId() : theirs.getId();
        MonsterplantBreedingRequests.Request request = requests.on(key);
        if (request == null
                || request.roomId() != room.getId()
                || !((request.requesterPetId() == mine.getId() && request.askedPetId() == theirs.getId())
                        || (request.requesterPetId() == theirs.getId() && request.askedPetId() == mine.getId())))
            return;
        if (!requests.close(request)) return;

        this.tell(room, request.requesterId(), new PetBreedingStartComposer(ANSWER_CANCELLED, 0, 0));
        this.tell(room, theirs.getUserId(), new PetBreedingStartComposer(ANSWER_CANCELLED, 0, 0));
        this.client.sendResponse(new PetBreedingStartComposer(ANSWER_CANCELLED, 0, 0));
    }

    private void accept(Room room, MonsterplantPet mine, MonsterplantPet theirs) {
        MonsterplantBreedingRequests.Request request =
                MonsterplantBreedingRequests.getInstance().on(mine.getId());

        // Only the plant that was asked for can accept, and only for the plant that asked.
        if (request == null
                || request.roomId() != room.getId()
                || request.requesterPetId() != theirs.getId()
                || request.requesterId() != theirs.getUserId()
                || room.getHabbo(theirs.getUserId()) == null) return;

        if (!MonsterplantBreedingRequests.getInstance().close(request)) return;

        this.tell(room, request.requesterId(), new PetBreedingStartComposer(ANSWER_ACCEPTED, 0, 0));
        this.client.sendResponse(new PetBreedingStartComposer(ANSWER_ACCEPTED, 0, 0));

        theirs.breed(mine);
    }

    private void tell(Room room, int userId, PetBreedingStartComposer composer) {
        Habbo habbo = room.getHabbo(userId);

        if (habbo != null && habbo.getClient() != null) habbo.getClient().sendResponse(composer);
    }

    private void legacy() throws Exception {
        int unknownInt = this.packet.readInt(); // Something state. 0 = initiate breeding

        if (unknownInt == 0) {
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            if (room == null) return;

            Pet petOne = room.getPet(this.packet.readInt());
            Pet petTwo = room.getPet(this.packet.readInt());

            if (petOne == null || petTwo == null || petOne == petTwo) {
                return;
            }

            if (petOne instanceof MonsterplantPet && petTwo instanceof MonsterplantPet) {
                MonsterplantPet plantOne = (MonsterplantPet) petOne;
                MonsterplantPet plantTwo = (MonsterplantPet) petTwo;

                // Validate both plants are breedable (fully grown, can breed, not dead)
                if (!plantOne.breedable() || !plantTwo.breedable()) {
                    return;
                }

                // Validate ownership - at least one plant must belong to the client
                // and the other must be publicly breedable or owned by client
                int clientId = this.client.getHabbo().getHabboInfo().getId();
                boolean ownsOne = plantOne.getUserId() == clientId;
                boolean ownsTwo = plantTwo.getUserId() == clientId;

                if (!ownsOne && !ownsTwo) {
                    // Client doesn't own either plant
                    return;
                }

                // If client doesn't own one of them, that one must be publicly breedable
                if (!ownsOne && !plantOne.isPubliclyBreedable()) {
                    return;
                }
                if (!ownsTwo && !plantTwo.isPubliclyBreedable()) {
                    return;
                }

                synchronized (room) {
                    plantOne.breed(plantTwo);
                }
            }
        }
    }
}
