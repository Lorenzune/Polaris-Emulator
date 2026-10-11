package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.wired.core.WiredFurnitureAnimationState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.WiredMovementMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WiredMovementsComposer extends MessageComposer {
    public static final int TYPE_USER_MOVE = 0;
    public static final int TYPE_FURNI_MOVE = 1;
    public static final int TYPE_WALL_ITEM_MOVE = 2;
    public static final int TYPE_USER_DIRECTION = 3;

    public static final int FURNI_ANCHOR_NONE = 0;
    public static final int FURNI_ANCHOR_USER = 1;
    public static final int FURNI_ANCHOR_FURNI = 2;

    public static final int USER_MOVEMENT_WALK = 0;
    public static final int USER_MOVEMENT_SLIDE = 1;
    public static final int DEFAULT_DURATION = 500;

    private final List<MovementData> movements;

    public WiredMovementsComposer(List<MovementData> movements) {
        this.movements = normalizeMovements(movements);
    }

    /** An instant furniture update supersedes earlier animations still waiting in the batch. */
    public static void discardFurnitureMovements(List<MovementData> movements, int itemId) {
        if (movements == null) return;
        movements.removeIf(movement -> movement instanceof FurniMovementData furniture && furniture.id == itemId);
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.movements.stream().anyMatch(movement -> movement instanceof FurniMovementData)) {
            return new WiredMovementMessage(this.movements);
        }
        this.response.init(Outgoing.WiredMovementsComposer);
        this.response.appendInt(this.movements.size());

        for (MovementData movement : this.movements) {
            this.response.appendInt(movement.getType());
            movement.append(this.response);
        }

        return this.response;
    }

    private static List<MovementData> normalizeMovements(List<MovementData> source) {
        if ((source == null) || source.isEmpty()) return new ArrayList<>();

        final List<MovementData> normalized = new ArrayList<>();
        final Map<Integer, Integer> furnitureIndexes = new HashMap<>();
        final Map<Integer, Integer> userIndexes = new HashMap<>();
        final java.util.Set<Integer> mergedUserIndexes = new java.util.HashSet<>();

        for (final MovementData movement : source) {
            if (movement == null) continue;
            if (movement instanceof UserMovementData next && next.movementType == USER_MOVEMENT_SLIDE) {
                Integer index = userIndexes.get(next.id);
                if (index != null) {
                    UserMovementData first = (UserMovementData) normalized.get(index);
                    normalized.set(
                            index,
                            new UserMovementData(
                                    next.id,
                                    first.fromX,
                                    first.fromY,
                                    next.toX,
                                    next.toY,
                                    first.fromZ,
                                    next.toZ,
                                    next.movementType,
                                    next.bodyDirection,
                                    next.headDirection,
                                    next.duration,
                                    next.jumpPower));
                    mergedUserIndexes.add(index);
                    continue;
                }
                userIndexes.put(next.id, normalized.size());
            } else if (movement instanceof UserMovementData next) {
                userIndexes.remove(next.id);
            } else if (movement instanceof UserDirectionData next) {
                userIndexes.remove(next.id);
            }
            // Effects in one execution batch mutate the same authoritative item.
            // Send its initial position and final destination once, rather than
            // letting a later height-only move overwrite the horizontal slide.
            if (movement instanceof FurniMovementData next) {
                Integer index = furnitureIndexes.get(next.id);
                if (index != null) {
                    FurniMovementData first = (FurniMovementData) normalized.get(index);
                    if (first.epoch != next.epoch) {
                        normalized.set(index, next);
                        continue;
                    }
                    normalized.set(
                            index,
                            new FurniMovementData(
                                    next.id,
                                    first.fromX,
                                    first.fromY,
                                    next.toX,
                                    next.toY,
                                    first.fromZ,
                                    next.toZ,
                                    next.rotation,
                                    next.duration,
                                    next.elapsed,
                                    next.anchorType,
                                    next.anchorId,
                                    next.overshootingDistance != null
                                            ? next.overshootingDistance
                                            : first.overshootingDistance,
                                    next.curveStrength != null ? next.curveStrength : first.curveStrength,
                                    next.epoch));
                    continue;
                }
                furnitureIndexes.put(next.id, normalized.size());
            }
            normalized.add(movement);
        }

        // A round trip through a service tile must not leak its temporary
        // direction. Authoritative room status retains the chair's posture.
        for (int index = normalized.size() - 1; index >= 0; index--) {
            if (normalized.get(index) instanceof UserMovementData movement
                    && movement.movementType == USER_MOVEMENT_SLIDE
                    && movement.jumpPower == null
                    && movement.fromX == movement.toX
                    && movement.fromY == movement.toY
                    && Double.compare(movement.fromZ, movement.toZ) == 0) {
                if (mergedUserIndexes.contains(index)) normalized.remove(index);
                else
                    normalized.set(
                            index, new UserDirectionData(movement.id, movement.headDirection, movement.bodyDirection));
            }
        }
        return normalized;
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, 0, DEFAULT_DURATION, 0, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(
            int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int duration) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, duration, 0, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int rotation,
            int duration,
            int elapsed) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, duration, elapsed, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int rotation,
            int duration,
            int elapsed,
            int anchorType,
            int anchorId) {
        return new FurniMovementData(
                id, fromX, fromY, toX, toY, fromZ, toZ, rotation, duration, elapsed, anchorType, anchorId, null, null);
    }

    public static MovementData furniMovement(
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int rotation,
            int duration,
            int elapsed,
            int anchorType,
            int anchorId,
            Integer overshootingDistance,
            Integer curveStrength) {
        return new FurniMovementData(
                id,
                fromX,
                fromY,
                toX,
                toY,
                fromZ,
                toZ,
                rotation,
                duration,
                elapsed,
                anchorType,
                anchorId,
                overshootingDistance,
                curveStrength);
    }

    public static MovementData userWalkMovement(
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int bodyDirection,
            int headDirection,
            int duration) {
        return new UserMovementData(
                id,
                fromX,
                fromY,
                toX,
                toY,
                fromZ,
                toZ,
                USER_MOVEMENT_WALK,
                bodyDirection,
                headDirection,
                duration,
                null);
    }

    public static MovementData userSlideMovement(
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int bodyDirection,
            int headDirection,
            int duration) {
        return new UserMovementData(
                id,
                fromX,
                fromY,
                toX,
                toY,
                fromZ,
                toZ,
                USER_MOVEMENT_SLIDE,
                bodyDirection,
                headDirection,
                duration,
                null);
    }

    public static MovementData userSlideMovement(
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int bodyDirection,
            int headDirection,
            int duration,
            Integer jumpPower) {
        return new UserMovementData(
                id,
                fromX,
                fromY,
                toX,
                toY,
                fromZ,
                toZ,
                USER_MOVEMENT_SLIDE,
                bodyDirection,
                headDirection,
                duration,
                jumpPower);
    }

    public static MovementData userDirectionUpdate(int id, int headDirection, int bodyDirection) {
        return new UserDirectionData(id, headDirection, bodyDirection);
    }

    public static MovementData wallItemMovement(int id, boolean enabled, int[] values) {
        return new WallItemMovementData(id, enabled, values);
    }

    public interface MovementData {
        int getType();

        default boolean isCurrent() {
            return true;
        }

        void append(ServerMessage response);
    }

    private abstract static class BaseMovementData implements MovementData {
        private final int type;

        private BaseMovementData(int type) {
            this.type = type;
        }

        @Override
        public int getType() {
            return this.type;
        }
    }

    private static final class UserMovementData extends BaseMovementData {
        private final int id;
        private final int fromX;
        private final int fromY;
        private final int toX;
        private final int toY;
        private final double fromZ;
        private final double toZ;
        private final int movementType;
        private final int bodyDirection;
        private final int headDirection;
        private final int duration;
        private final Integer jumpPower;

        private UserMovementData(
                int id,
                int fromX,
                int fromY,
                int toX,
                int toY,
                double fromZ,
                double toZ,
                int movementType,
                int bodyDirection,
                int headDirection,
                int duration,
                Integer jumpPower) {
            super(TYPE_USER_MOVE);
            this.id = id;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromZ = fromZ;
            this.toZ = toZ;
            this.movementType = movementType;
            this.bodyDirection = bodyDirection;
            this.headDirection = headDirection;
            this.duration = duration;
            this.jumpPower = jumpPower;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.fromX);
            response.appendInt(this.fromY);
            response.appendInt(this.toX);
            response.appendInt(this.toY);
            response.appendString(Double.toString(this.fromZ));
            response.appendString(Double.toString(this.toZ));
            response.appendInt(this.id);
            response.appendInt(this.movementType);
            response.appendInt(this.bodyDirection);
            response.appendInt(this.headDirection);
            response.appendInt(this.duration);
            response.appendBoolean(this.jumpPower != null);
            if (this.jumpPower != null) {
                response.appendInt(this.jumpPower);
            }
        }
    }

    private static final class FurniMovementData extends BaseMovementData {
        private final WiredFurnitureAnimationState.Epoch epoch;
        private final int id;
        private final int fromX;
        private final int fromY;
        private final int toX;
        private final int toY;
        private final double fromZ;
        private final double toZ;
        private final int rotation;
        private final int duration;
        private final int elapsed;
        private final int anchorType;
        private final int anchorId;
        private final Integer overshootingDistance;
        private final Integer curveStrength;

        private FurniMovementData(
                int id,
                int fromX,
                int fromY,
                int toX,
                int toY,
                double fromZ,
                double toZ,
                int rotation,
                int duration,
                int elapsed,
                int anchorType,
                int anchorId,
                Integer overshootingDistance,
                Integer curveStrength) {
            this(
                    id,
                    fromX,
                    fromY,
                    toX,
                    toY,
                    fromZ,
                    toZ,
                    rotation,
                    duration,
                    elapsed,
                    anchorType,
                    anchorId,
                    overshootingDistance,
                    curveStrength,
                    WiredFurnitureAnimationState.capture(id));
        }

        private FurniMovementData(
                int id,
                int fromX,
                int fromY,
                int toX,
                int toY,
                double fromZ,
                double toZ,
                int rotation,
                int duration,
                int elapsed,
                int anchorType,
                int anchorId,
                Integer overshootingDistance,
                Integer curveStrength,
                WiredFurnitureAnimationState.Epoch epoch) {
            super(TYPE_FURNI_MOVE);
            this.epoch = epoch;
            this.id = id;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromZ = fromZ;
            this.toZ = toZ;
            this.rotation = rotation;
            this.duration = duration;
            this.elapsed = elapsed;
            this.anchorType = anchorType;
            this.anchorId = anchorId;
            this.overshootingDistance = overshootingDistance;
            this.curveStrength = curveStrength;
        }

        @Override
        public boolean isCurrent() {
            return this.epoch.isCurrent();
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.fromX);
            response.appendInt(this.fromY);
            response.appendInt(this.toX);
            response.appendInt(this.toY);
            response.appendString(Double.toString(this.fromZ));
            response.appendString(Double.toString(this.toZ));
            response.appendInt(this.id);
            response.appendInt(this.duration);
            response.appendInt(this.rotation);
            response.appendBoolean(this.overshootingDistance != null);
            if (this.overshootingDistance != null) {
                response.appendInt(this.overshootingDistance);
            }
            response.appendBoolean(this.curveStrength != null);
            if (this.curveStrength != null) {
                response.appendInt(this.curveStrength);
            }
        }
    }

    private static final class UserDirectionData extends BaseMovementData {
        private final int id;
        private final int headDirection;
        private final int bodyDirection;

        private UserDirectionData(int id, int headDirection, int bodyDirection) {
            super(TYPE_USER_DIRECTION);
            this.id = id;
            this.headDirection = headDirection;
            this.bodyDirection = bodyDirection;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.id);
            response.appendInt(this.headDirection);
            response.appendInt(this.bodyDirection);
        }
    }

    private static final class WallItemMovementData extends BaseMovementData {
        private final int id;
        private final boolean enabled;
        private final int[] values;

        private WallItemMovementData(int id, boolean enabled, int[] values) {
            super(TYPE_WALL_ITEM_MOVE);
            this.id = id;
            this.enabled = enabled;
            this.values = values == null ? new int[9] : values;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.id);
            response.appendBoolean(this.enabled);

            for (int index = 0; index < 9; index++) {
                response.appendInt(index < this.values.length ? this.values[index] : 0);
            }
        }
    }
}
