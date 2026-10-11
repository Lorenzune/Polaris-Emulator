package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.pets.breeding.NestBreedingGuard;
import com.eu.habbo.habbohotel.pets.breeding.PetBreedingPlatform;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.NestBreedingSuccessComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingCompleted;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingFailedComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingResultComposer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractionPetBreedingNest extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionPetBreedingNest.class);
    public volatile Pet petOne;
    public volatile Pet petTwo;
    private boolean processing;
    private long generation;
    private ScheduledFuture<?> timeout;

    public InteractionPetBreedingNest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionPetBreedingNest(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public void serializeExtradata(ServerMessage message) {
        message.appendInt(this.isLimited() ? 256 : 0);
        message.appendString(this.getExtradata());
        super.serializeExtradata(message);
    }

    private boolean compatible(Pet pet) {
        if (pet == null || pet.getPetData() == null || pet.getPetData().getOffspringType() < 0) return false;
        return this.getBaseItem()
                .getName()
                .equalsIgnoreCase("pet_breeding_" + pet.getPetData().getName());
    }

    @Override
    public boolean canWalkOn(RoomUnit unit, Room room, Object[] objects) {
        Pet pet = room.getPet(unit);
        return compatible(pet) && !(pet instanceof RideablePet ride && ride.getRider() != null) && !boxFull();
    }

    @Override
    public boolean canOverrideTile(RoomUnit unit, Room room, RoomTile tile) {
        return unit.getRoomUnitType() == RoomUnitType.PET && canWalkOn(unit, room, null);
    }

    @Override
    public void onWalk(RoomUnit unit, Room room, Object[] objects) {}

    public boolean boxFull() {
        return petOne != null && petTwo != null;
    }

    public boolean addPet(Pet pet) {
        Room room = pet == null ? null : pet.getRoom();
        if (room == null) return false;
        synchronized (room) {
            if (!compatible(pet) || processing || petOne == pet || petTwo == pet) return false;
            if (petOne == null) petOne = pet;
            else if (petTwo == null) petTwo = pet;
            else return false;
            pet.getRoomUnit().setCanWalk(false);
            armTimeout(room);
            return true;
        }
    }

    @Override
    public void onWalkOn(RoomUnit unit, Room room, Object[] objects) {
        synchronized (room) {
            if (!addPet(room.getPet(unit)) || !boxFull()) return;
            Habbo one = room.getHabbo(petOne.getUserId()), two = room.getHabbo(petTwo.getUserId());
            if (one == null
                    || two == null
                    || petOne.getPetData().getType() != petTwo.getPetData().getType()) {
                reset(room);
                return;
            }
            setExtradata("1");
            room.updateItem(this);
            if (PetBreedingPlatform.services().config().get().getBoolean("pet.breeding.confirmation.enabled", false)) {
                PetBreedingResultComposer request = new PetBreedingResultComposer(
                        getId(),
                        petOne.getPetData().getOffspringType(),
                        petOne,
                        one.getHabboInfo().getUsername(),
                        petTwo,
                        two.getHabboInfo().getUsername());
                one.getClient().sendResponse(request);
                if (one != two) two.getClient().sendResponse(request);
            } else {
                String oneName = petOne.getName(), twoName = petTwo.getName();
                String name = oneName.substring(0, Math.max(1, oneName.length() / 2))
                        + twoName.substring(twoName.length() / 2);
                if (name.length() < CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM) name = "Baby";
                breed(two, name.substring(0, Math.min(15, name.length())), petOne.getId(), petTwo.getId());
            }
        }
    }

    private void armTimeout(Room room) {
        if (timeout != null) timeout.cancel(false);
        long token = ++generation;
        timeout = PetBreedingPlatform.services()
                .threading()
                .get()
                .getService()
                .schedule(
                        () -> {
                            synchronized (room) {
                                if (generation == token && !processing) reset(room);
                            }
                        },
                        60,
                        TimeUnit.SECONDS);
    }

    private void tell(Room room, int code) {
        Habbo one = petOne == null ? null : room.getHabbo(petOne.getUserId());
        Habbo two = petTwo == null ? null : room.getHabbo(petTwo.getUserId());
        if (one != null) one.getClient().sendResponse(new PetBreedingFailedComposer(getId(), code));
        if (two != null && two != one) two.getClient().sendResponse(new PetBreedingFailedComposer(getId(), code));
    }

    private void reset(Room room) {
        ++generation;
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
        tell(room, 0);
        for (Pet pet : new Pet[] {petOne, petTwo}) {
            if (pet != null) {
                pet.getRoomUnit().setCanWalk(true);
                pet.setTask(PetTasks.FREE);
            }
        }
        petOne = null;
        petTwo = null;
        processing = false;
        setExtradata("0");
        if (room.getHabboItem(getId()) == this) room.updateItem(this);
    }

    @Override
    public void onWalkOff(RoomUnit unit, Room room, Object[] objects) {
        synchronized (room) {
            if ((petOne != null && petOne.getRoomUnit() == unit) || (petTwo != null && petTwo.getRoomUnit() == unit))
                reset(room);
        }
    }

    @Override
    public void onPickUp(Room room) {
        synchronized (room) {
            reset(room);
            super.onPickUp(room);
        }
    }

    @Override
    public void onMove(Room room, RoomTile oldTile, RoomTile newTile) {
        synchronized (room) {
            reset(room);
            super.onMove(room, oldTile, newTile);
        }
    }

    public void stopBreeding(Habbo habbo) {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;
        synchronized (room) {
            int sender = habbo.getHabboInfo().getId();
            if (!processing
                    && ((petOne != null && petOne.getUserId() == sender)
                            || (petTwo != null && petTwo.getUserId() == sender))) reset(room);
        }
    }

    public void onOwnerLeave(Room room, int ownerId) {
        synchronized (room) {
            if ((petOne != null && petOne.getUserId() == ownerId) || (petTwo != null && petTwo.getUserId() == ownerId))
                reset(room);
        }
    }

    public void breed(Habbo habbo, String name, int oneId, int twoId) {
        Room room = habbo == null ? null : habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;
        synchronized (room) {
            if (processing || !boxFull() || room.getHabboItem(getId()) != this) return;
            if (!NestBreedingGuard.matches(
                    habbo.getHabboInfo().getId(),
                    petOne.getUserId(),
                    petTwo.getUserId(),
                    petOne.getId(),
                    petTwo.getId(),
                    oneId,
                    twoId)) return;
            if (room.getPet(petOne.getId()) != petOne
                    || room.getPet(petTwo.getId()) != petTwo
                    || room.getHabbo(petOne.getUserId()) == null
                    || room.getHabbo(petTwo.getUserId()) == null) {
                tell(room, 2);
                reset(room);
                return;
            }
            if (name == null
                    || name.length() < CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM
                    || name.length() > Math.min(15, CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM)
                    || !StringUtils.isAlphanumeric(name)) {
                habbo.getClient().sendResponse(new PetBreedingFailedComposer(getId(), 3));
                return;
            }
            processing = true;
            if (timeout != null) timeout.cancel(false);
            long token = ++generation;
            setExtradata("2");
            room.updateItem(this);
            PetBreedingPlatform.services().threading().get().run(() -> complete(room, habbo, name, token), 2000);
        }
    }

    private void complete(Room room, Habbo owner, String name, long token) {
        synchronized (room) {
            if (generation != token || !processing) return;
            if (room.getHabboItem(getId()) != this
                    || !boxFull()
                    || room.getPet(petOne.getId()) != petOne
                    || room.getPet(petTwo.getId()) != petTwo
                    || owner.getHabboInfo().getCurrentRoom() != room
                    || room.getHabbo(petOne.getUserId()) == null
                    || room.getHabbo(petTwo.getUserId()) == null) {
                reset(room);
                return;
            }
            Pet offspring = null;
            try {
                int type = petOne.getPetData().getOffspringType();
                int score = (int) Math.min(
                        20,
                        Math.max(
                                1,
                                Math.round(PetManager.getNormalDistributionForBreeding(
                                                ((double) petOne.getLevel() + petTwo.getLevel()) / 2)
                                        .sample())));
                int bucket = score <= 10 ? 1 : score <= 15 ? 2 : score <= 18 ? 3 : 4;
                var rewards = PetBreedingPlatform.services()
                        .game()
                        .get()
                        .getPetManager()
                        .getBreedingRewards(type);
                var choices = rewards == null ? null : rewards.get(bucket);
                if (choices == null || choices.isEmpty())
                    throw new IllegalStateException("Missing breeding rewards " + type);
                int race = PetBreedingPlatform.services()
                                .config()
                                .get()
                                .getBoolean("pet.breeding.confirmation.enabled", false)
                        ? choices.get(PetBreedingPlatform.services()
                                        .random()
                                        .get()
                                        .nextInt(choices.size()))
                                .breed
                        : score;
                offspring =
                        new Pet(type, race, "FFFFFF", name, owner.getHabboInfo().getId());
                offspring.needsUpdate = true;
                // Creating the pet and consuming its nest must commit together.
                try (Connection connection = PetBreedingPlatform.services()
                        .database()
                        .get()
                        .getDataSource()
                        .getConnection()) {
                    com.eu.habbo.habbohotel.pets.breeding.PetBreedingPersistence.createFromNest(
                            connection, offspring, getId(), room.getId());
                }
            } catch (Exception failure) {
                LOGGER.error("Unable to breed pets in nest {}", getId(), failure);
                tell(room, 2);
                reset(room);
                return;
            }
            Pet child = offspring;
            Habbo other = room.getHabbo(
                    petOne.getUserId() == owner.getHabboInfo().getId() ? petTwo.getUserId() : petOne.getUserId());
            reset(room);
            room.removeHabboItem(this);
            room.placePet(child, getX(), getY(), getZ(), getRotation());
            child.needsUpdate = true;
            PetBreedingPlatform.services().threading().get().runPersistence(child);
            if (PetBreedingPlatform.services().config().get().getBoolean("pet.breeding.confirmation.enabled", false)) {
                NestBreedingSuccessComposer result = new NestBreedingSuccessComposer(
                        child.getId(),
                        PetBreedingPlatform.services()
                                .game()
                                .get()
                                .getPetManager()
                                .getRarityForOffspring(child));
                owner.getClient().sendResponse(result);
                if (other != null && other != owner) other.getClient().sendResponse(result);
            } else {
                owner.getClient().sendResponse(new PetBreedingCompleted(PetBreedingCompleted.STATE_ACCEPT, 0, 0));
            }
            String kind = getBaseItem().getName().substring("pet_breeding_".length());
            AchievementManager.progressAchievement(
                    owner,
                    PetBreedingPlatform.services()
                            .game()
                            .get()
                            .getAchievementManager()
                            .getAchievement(Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + "Breeder"));
        }
    }
}
