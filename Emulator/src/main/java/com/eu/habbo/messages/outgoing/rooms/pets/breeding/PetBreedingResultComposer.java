package com.eu.habbo.messages.outgoing.rooms.pets.breeding;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetBreedingReward;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import org.apache.commons.math3.distribution.NormalDistribution;

public class PetBreedingResultComposer extends MessageComposer {
    private final int boxId;
    private final int petType;
    private final PetBreedingPet petOne;
    private final PetBreedingPet petTwo;

    public PetBreedingResultComposer(
            int boxId, int petType, Pet petOne, String ownerPetOne, Pet petTwo, String ownerPetTwo) {
        this.boxId = boxId;
        this.petType = petType;
        this.petOne = new PetBreedingPet(petOne, ownerPetOne);
        this.petTwo = new PetBreedingPet(petTwo, ownerPetTwo);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetBreedingResultComposer);
        this.response.appendInt(this.boxId);
        this.petOne.serialize(this.response);
        this.petTwo.serialize(this.response);

        double avgLevel = ((float) this.petOne.pet.getLevel() + this.petTwo.pet.getLevel()) / 2;
        NormalDistribution normalDistribution = PetManager.getNormalDistributionForBreeding(avgLevel);

        Int2ObjectMap<ArrayList<PetBreedingReward>> rewardBreeds =
                Emulator.getGameEnvironment().getPetManager().getBreedingRewards(this.petType);

        this.response.appendInt(4); // Levels
        {
            int[] odds = com.eu.habbo.habbohotel.pets.breeding.PetBreedingOdds.percentages(normalDistribution);
            int percentage1 = odds[0], percentage2 = odds[1], percentage3 = odds[2], percentage4 = odds[3];

            this.response.appendInt(percentage4); // Percentage
            this.response.appendInt(
                    (rewardBreeds == null || rewardBreeds.get(4) == null
                            ? 0
                            : rewardBreeds.get(4).size())); // Count
            {
                for (PetBreedingReward reward : (rewardBreeds == null || rewardBreeds.get(4) == null
                        ? new ArrayList<PetBreedingReward>()
                        : rewardBreeds.get(4))) {
                    this.response.appendInt(reward.breed);
                }
            }

            this.response.appendInt(percentage3); // Percentage
            this.response.appendInt(
                    (rewardBreeds == null || rewardBreeds.get(3) == null
                            ? 0
                            : rewardBreeds.get(3).size())); // Count
            {
                for (PetBreedingReward reward : (rewardBreeds == null || rewardBreeds.get(3) == null
                        ? new ArrayList<PetBreedingReward>()
                        : rewardBreeds.get(3))) {
                    this.response.appendInt(reward.breed);
                }
            }

            this.response.appendInt(percentage2); // Percentage
            this.response.appendInt(
                    (rewardBreeds == null || rewardBreeds.get(2) == null
                            ? 0
                            : rewardBreeds.get(2).size())); // Count
            {
                for (PetBreedingReward reward : (rewardBreeds == null || rewardBreeds.get(2) == null
                        ? new ArrayList<PetBreedingReward>()
                        : rewardBreeds.get(2))) {
                    this.response.appendInt(reward.breed);
                }
            }

            this.response.appendInt(percentage1); // Percentage
            this.response.appendInt(
                    (rewardBreeds == null || rewardBreeds.get(1) == null
                            ? 0
                            : rewardBreeds.get(1).size())); // Count
            {
                for (PetBreedingReward reward : (rewardBreeds == null || rewardBreeds.get(1) == null
                        ? new ArrayList<PetBreedingReward>()
                        : rewardBreeds.get(1))) {
                    this.response.appendInt(reward.breed);
                }
            }
        }

        this.response.appendInt(this.petType); // Race type
        return this.response;
    }

    public class PetBreedingPet implements ISerialize {
        public final Pet pet;
        public final String ownerName;

        public PetBreedingPet(Pet pet, String ownerName) {
            this.pet = pet;
            this.ownerName = ownerName;
        }

        @Override
        public void serialize(ServerMessage message) {
            message.appendInt(this.pet.getId());
            message.appendString(this.pet.getName());
            message.appendInt(this.pet.getLevel());
            message.appendString(
                    this.pet.getPetData().getType() + " " + this.pet.getRace() + " " + this.pet.getColor());
            message.appendString(this.ownerName);
        }

        public Pet getPet() {
            return pet;
        }

        public String getOwnerName() {
            return ownerName;
        }
    }

    public int getBoxId() {
        return boxId;
    }

    public int getPetType() {
        return petType;
    }

    public PetBreedingPet getPetOne() {
        return petOne;
    }

    public PetBreedingPet getPetTwo() {
        return petTwo;
    }
}
