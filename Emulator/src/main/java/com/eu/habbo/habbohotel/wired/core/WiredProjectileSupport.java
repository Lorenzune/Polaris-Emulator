package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraProjectile;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Native projectile animation. Logical furniture placement remains at the Wired action target. */
public final class WiredProjectileSupport {
    private static final int VARIABLE_OFFSET = 1_900_000_000;
    private static final String[] KEYS = {"animation.tiles_travelled", "animation.user_collisions", "animation.furni_collisions", "animation.position.x", "animation.position.y", "animation.position.altitude", "animation.is_travelling"};
    // Weak room ownership prevents animation values retaining unloaded rooms.
    private static final Map<Room, Map<Integer, Flight>> FLIGHTS = Collections.synchronizedMap(new WeakHashMap<>());
    private WiredProjectileSupport() {}

    public record Plan(WiredExtraProjectile extra, int rotation, int duration, Integer overshoot, Integer curve, double ratio, int mask) {}

    public static Plan resolve(Room room, HabboItem stack, HabboItem moving, RoomTile target, Double z, int rotation, int duration, WiredContext ctx) {
        if (room == null || stack == null || moving == null || target == null || room.getRoomSpecialTypes() == null) return null;
        var extras = room.getRoomSpecialTypes().getExtras(stack.getX(), stack.getY());
        if (extras == null) return null;
        for (var extra : WiredExecutionOrderUtil.sort(extras)) if (extra instanceof WiredExtraProjectile projectile) {
            int[] p = projectile.getIntParams();
            if (p.length > 19 && p[19] != 0) {
                if (ctx == null) continue;
                List<HabboItem> selected = new ArrayList<>();
                for (int id : projectile.getSelectedFurniIds()) { HabboItem item = room.getHabboItem(id); if (item != null) selected.add(item); }
                if (WiredSourceUtil.resolveItems(ctx, p[19], selected).stream().noneMatch(item -> item.getId() == moving.getId())) continue;
            }
            double dx = target.x - moving.getX(), dy = target.y - moving.getY();
            double dz = z == null ? target.getStackHeight() - moving.getZ() : z - moving.getZ();
            if (z == null && extras.stream().anyMatch(e -> e instanceof com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovePhysics physics && physics.isKeepAltitude())) dz = 0;
            double horizontal = Math.hypot(dx, dy);
            int facing = p[0] != 0 && horizontal > 0 ? direction(dx, dy, p[1], p[10]) : rotation;
            double visualDistance = horizontal;
            if (p[14] != 0) {
                Integer distance = value(projectile, ctx, 1);
                if (distance != null) visualDistance = Math.max(0.001, p[14] == 1 ? horizontal + Math.max(-64, Math.min(64, distance)) : Math.max(0, Math.min(64, distance)));
            }
            double ratio = horizontal > 0 ? visualDistance / horizontal : 1;
            if (p[2] != 0) {
                Integer tileTime = value(projectile, ctx, 0);
                if (tileTime != null) {
                    double length = Math.sqrt((p[6] != 0 ? dx * dx : 0) + (p[7] != 0 ? dy * dy : 0) + (p[8] != 0 ? dz * dz : 0));
                    double perTile = Math.max(1, Math.min(100000, tileTime) - Math.max(0, length - 1) * p[9]);
                    duration = (int) Math.max(1, Math.min(100000, length * perTile));
                }
            }
            Integer overshoot = horizontal > 0 && p[14] != 0 ? (int) Math.max(1 - duration, Math.min(100000 - duration, Math.round(duration * (ratio - 1)))) : null;
            ratio = overshoot == null ? 1 : (duration + overshoot) / (double) duration;
            return new Plan(projectile, facing, duration, overshoot, p[18] == 0 ? null : p[18], ratio, p[11]);
        }
        return null;
    }

    public static int direction(double dx, double dy, int system, int offset) {
        if (dx == 0 && dy == 0) return Math.floorMod(offset, 8);
        double angle = Math.atan2(dx, -dy) / (Math.PI / 4);
        int rotation;
        if (system == 0 && Math.abs(dx) != Math.abs(dy)) rotation = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 2 : 6) : (dy > 0 ? 4 : 0);
        else if (system == 2) rotation = (int) Math.round(angle / 2) * 2;
        else if (system == 3) rotation = (int) Math.floor(angle / 2) * 2 + 1;
        else rotation = (int) Math.round(angle);
        return Math.floorMod(rotation + offset, 8);
    }

    private static Integer value(WiredExtraProjectile extra, WiredContext ctx, int slot) {
        int[] p = extra.getIntParams();
        int mode = slot == 0 ? 3 : 15, literal = slot == 0 ? 4 : 16, target = slot == 0 ? 5 : 17;
        if (p[mode] != 1) return p[literal];
        if (ctx == null || ctx.room() == null) return null;
        String token = extra.getVariableIds()[slot]; if (token == null || token.isBlank()) return null;
        Room room = ctx.room(); boolean internal = token.startsWith("internal:"); String key = internal ? token.substring(9) : "";
        int id = 0;
        if (!internal) try { id = Integer.parseInt(token.replace("custom:", "")); } catch (NumberFormatException ignored) { return null; }
        int source = p.length > 21 + slot ? p[21 + slot] : 0;
        switch (p[target]) {
            case -10: return internal ? WiredInternalVariableSupport.readRoomValue(room, key) : room.getRoomVariableManager().hasVariable(id) ? room.getRoomVariableManager().getCurrentValue(id) : null;
            case -20: return internal ? WiredInternalVariableSupport.readContextValue(ctx, key) : WiredContextVariableSupport.getCurrentValue(ctx, id);
            case 1:
                for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, source)) {
                    var habbo = room.getHabbo(unit);
                    Integer v = internal ? WiredInternalVariableSupport.readUserValue(room, unit, key) : habbo != null && room.getUserVariableManager().hasVariable(habbo.getHabboInfo().getId(), id) ? room.getUserVariableManager().getCurrentValue(habbo.getHabboInfo().getId(), id) : null;
                    if (v != null) return v;
                } break;
            case 0:
                List<HabboItem> selected = new ArrayList<>();
                for (int fid : extra.getSelectedFurniIds()) { HabboItem item = room.getHabboItem(fid); if (item != null) selected.add(item); }
                for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, selected)) {
                    Integer v = internal ? WiredInternalVariableSupport.readFurniValue(room, item, key) : room.getFurniVariableManager().hasVariable(item.getId(), id) ? room.getFurniVariableManager().getCurrentValue(item.getId(), id) : null;
                    if (v != null) return v;
                } break;
        }
        return null;
    }

    public static void begin(Room room, HabboItem item, RoomTile from, double fromZ, RoomTile target, Plan plan, Integer curve, int elapsed, WiredContext ctx, boolean animated) {
        int[] p = plan.extra().getIntParams();
        if (p[0] != 0 && p[12] != 0 && ctx != null) {
            int source = p.length > 20 ? p[20] : 0;
            List<WiredMovementsComposer.MovementData> updates = new ArrayList<>();
            for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, source)) {
                // Cosmetic: do not change authoritative direction or location.
                updates.add(WiredMovementsComposer.userDirectionUpdate(unit.getId(), plan.rotation(), plan.rotation()));
                if (p[13] != 0 && unit.getCurrentLocation() != null && RoomUserRotation.rotationDistance(unit.getBodyRotation().getValue(), plan.rotation()) >= 2) {
                    var tile = unit.getCurrentLocation();
                    updates.add(WiredMovementsComposer.userSlideMovement(unit.getId(), tile.x, tile.y, tile.x, tile.y, unit.getZ(), unit.getZ(), plan.rotation(), plan.rotation(), 200, 100));
                }
            }
            if (!updates.isEmpty()) room.sendComposer(new WiredMovementsComposer(updates).compose());
        }
        if (plan.mask() == 0) { map(room).remove(item.getId()); return; }
        Flight flight = new Flight(plan.extra(), item, from.x, from.y, fromZ, target.x, target.y, item.getZ(), plan, curve, elapsed, animated);
        map(room).put(item.getId(), flight);
        update(room, flight);
    }

    private static Map<Integer, Flight> map(Room room) { synchronized (FLIGHTS) { return FLIGHTS.computeIfAbsent(room, r -> new ConcurrentHashMap<>()); } }
    private static void update(Room room, Flight flight) {
        synchronized (flight) { updateFlight(room, flight); }
    }
    private static void updateFlight(Room room, Flight flight) {
        if (!room.isLoaded() || room.getHabboItem(flight.item.getId()) != flight.item || room.getRoomSpecialTypes().getExtra(flight.extra.getId()) != flight.extra || map(room).get(flight.item.getId()) != flight) { map(room).remove(flight.item.getId(), flight); return; }
        flight.sample(System.nanoTime());
        int endTravelled = flight.values[0];
        if (flight.duration == 0) { flight.lastX=flight.x; flight.lastY=flight.y; flight.lastZ=flight.z; }
        // Sample all crossed cells, including fast flights that travel several tiles per tick.
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(flight.x - flight.lastX, flight.y - flight.lastY) * 2));
        steps = Math.min(512, steps);
        for (int step = 0; step <= steps; step++) {
            if (map(room).get(flight.item.getId()) != flight) return;
            double t = step / (double) steps;
            double sampleX = flight.lastX + (flight.x - flight.lastX) * t;
            double sampleY = flight.lastY + (flight.y - flight.lastY) * t;
            double sampleZ = flight.lastZ + (flight.z - flight.lastZ) * t;
            flight.values[0]=(int)Math.floor(Math.hypot(sampleX-flight.fromX,sampleY-flight.fromY));
            flight.values[3]=(int)Math.round(sampleX*100); flight.values[4]=(int)Math.round(sampleY*100); flight.values[5]=(int)Math.round(sampleZ*100);
            emitChange(room, flight, 0);
            RoomTile tile = room.getLayout().getTile((short) Math.round(sampleX), (short) Math.round(sampleY));
            if (tile == null) continue;
            for (RoomUnit unit : room.getRoomUnits(tile)) if (unit != null && ((flight.mask & 2) != 0) && Math.abs(unit.getZ() - sampleZ) < 1 && flight.users.add(unit.getId())) {
                flight.values[1]++;
                emitChange(room, flight, 1);
                WiredManager.triggerBotCollision(room, unit, flight.item);
            }
            for (HabboItem other : room.getItemsAt(tile)) if (other != null && other.getId() != flight.item.getId() && ((flight.mask & 4) != 0) && Math.abs(other.getZ() - sampleZ) <= com.eu.habbo.habbohotel.items.Item.getCurrentHeight(other) + 0.1 && flight.furni.add(other.getId())) { flight.values[2]++; emitChange(room, flight, 2); }
        }
        flight.lastX = flight.x; flight.lastY = flight.y; flight.lastZ = flight.z;
        flight.values[0]=endTravelled; flight.values[3]=(int)Math.round(flight.x*100); flight.values[4]=(int)Math.round(flight.y*100); flight.values[5]=(int)Math.round(flight.z*100);
        emitChange(room, flight, 0); emitChange(room, flight, 6);
        if (map(room).get(flight.item.getId()) != flight) return;
        if ((flight.values[6] == 0) || System.nanoTime() - flight.lastBroadcast > 250_000_000L) {
            room.getFurniVariableManager().requestBroadcast(); flight.lastBroadcast = System.nanoTime();
        }
        if (flight.values[6] != 0) room.getWiredRuntime().scheduleProjectileUpdate(() -> update(room, flight), Math.max(1, Math.min(50, flight.duration - (int)((System.nanoTime() - flight.start) / 1_000_000L))));
    }

    private static void emitChange(Room room, Flight flight, int index) {
        if ((flight.mask & (1 << index)) == 0 || flight.values[index] == flight.previous[index]) return;
        int id = variableId(flight.extra.getId(), index);
        WiredEvent.VariableChangeKind kind = flight.values[index] > flight.previous[index] ? WiredEvent.VariableChangeKind.INCREASED : WiredEvent.VariableChangeKind.DECREASED;
        flight.previous[index] = flight.values[index];
        if (id > 0) WiredManager.triggerFurniVariableChanged(room, flight.item.getId(), id, false, false, kind);
    }

    public static void cancelItem(Room room, HabboItem item) { if (room != null && item != null) map(room).remove(item.getId()); }
    public static void cancelExtra(WiredExtraProjectile extra) { synchronized (FLIGHTS) { for (var flights : FLIGHTS.values()) flights.values().removeIf(f -> f.extra == extra); } }
    public static int variableId(int boxId, int index) { long id = VARIABLE_OFFSET + (long) boxId * 8 + index; return boxId > 0 && index >= 0 && index < 7 && id <= Integer.MAX_VALUE ? (int) id : 0; }
    private static WiredExtraProjectile box(Room room, int id) {
        if (room == null || room.getRoomSpecialTypes() == null || id < VARIABLE_OFFSET) return null;
        var extra = room.getRoomSpecialTypes().getExtra((id - VARIABLE_OFFSET) / 8);
        if (!(extra instanceof WiredExtraProjectile projectile) || (id - VARIABLE_OFFSET) % 8 >= 7 || (projectile.getIntParams()[11] & (1 << ((id - VARIABLE_OFFSET) % 8))) == 0) return null;
        return projectile;
    }
    public static WiredVariableDefinitionInfo definition(Room room, int id) { return box(room, id) == null ? null : new WiredVariableDefinitionInfo(id, KEYS[(id - VARIABLE_OFFSET) % 8], true, 0, false, true); }
    public static List<WiredVariableDefinitionInfo> definitions(Room room) {
        List<WiredVariableDefinitionInfo> result = new ArrayList<>();
        if (room != null && room.getRoomSpecialTypes() != null) for (var extra : room.getRoomSpecialTypes().getExtras()) if (extra instanceof WiredExtraProjectile projectile) for (int i = 0; i < 7; i++) {
            int id = variableId(extra.getId(), i); if (id > 0 && (projectile.getIntParams()[11] & (1 << i)) != 0) result.add(new WiredVariableDefinitionInfo(id, KEYS[i], true, 0, false, true));
        }
        return result;
    }
    public static Integer read(Room room, int furniId, int definitionId) {
        WiredExtraProjectile extra = box(room, definitionId); if (extra == null) return null;
        Flight flight = map(room).get(furniId); if (flight == null || flight.extra != extra || room.getHabboItem(furniId) != flight.item) return null;
        synchronized (flight) { return flight.values[(definitionId - VARIABLE_OFFSET) % 8]; }
    }

    private static final class Flight {
        final WiredExtraProjectile extra; final HabboItem item; final double fromX, fromY, fromZ, dx, dy, dz; final long start; final int duration, mask; final Integer curve;
        final int[] values = new int[7], previous = new int[7]; final Set<Integer> users = new HashSet<>(), furni = new HashSet<>(); double x,y,z,lastX,lastY,lastZ; long lastBroadcast;
        Flight(WiredExtraProjectile extra, HabboItem item, double fx, double fy, double fz, double tx, double ty, double tz, Plan plan, Integer curve, int elapsed, boolean animated) {
            this.extra=extra; this.item=item; fromX=fx; fromY=fy; fromZ=fz; dx=(tx-fx)*(animated?plan.ratio():1); dy=(ty-fy)*(animated?plan.ratio():1); dz=tz-fz;
            duration=animated ? Math.max(1, plan.duration()+(plan.overshoot()==null?0:plan.overshoot())) : 0; mask=plan.mask(); this.curve=curve; start=System.nanoTime()-Math.max(0,elapsed)*1_000_000L;
            previous[6]=0; lastX=fx; lastY=fy; lastZ=fz;
        }
        void sample(long now) {
            double t=duration==0?1:Math.min(1,Math.max(0,(now-start)/1_000_000.0/duration));
            x=fromX+dx*t; y=fromY+dy*t; z=fromZ+dz*t+(curve==null?0:curve/100.0*Math.sqrt(dx*dx+dy*dy+dz*dz)*t*(1-t));
            values[0]=(int)Math.floor(Math.hypot(dx,dy)*t); values[3]=(int)Math.round(x*100); values[4]=(int)Math.round(y*100); values[5]=(int)Math.round(z*100); values[6]=t<1?1:0;
        }
    }
}
