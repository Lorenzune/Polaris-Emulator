package com.eu.habbo.habbohotel.navigation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public abstract class NavigatorFilter {
    public final String viewName;

    public NavigatorFilter(String viewName) {
        this.viewName = viewName;
    }

    public void filter(Method method, Object value, List<SearchResultList> collection) {
        if (method == null) {
            return;
        }

        if (value instanceof String) {
            if (((String) value).isEmpty()) {
                return;
            }
        }

        for (SearchResultList result : collection) {
            if (!result.filter) {
                continue;
            }

            this.filterRooms(method, value, result.rooms);
        }
    }

    public void filterRooms(Method method, Object value, List<Room> result) {
        if (method == null || value == null) {
            return;
        }

        String strVal = value.toString().trim();
        if (strVal.isEmpty()) {
            return;
        }

        List<Room> toRemove = new ArrayList<>();
        try {
            method.setAccessible(true);

            for (Room room : result) {
                if (method.getName().equals("getTags")) {
                    String tags = room.getTags();
                    boolean matchesTag = false;
                    if (tags != null && !tags.isEmpty()) {
                        for (String t : tags.split(";")) {
                            if (t.trim().equalsIgnoreCase(strVal)) {
                                matchesTag = true;
                                break;
                            }
                        }
                    }
                    if (!matchesTag) {
                        toRemove.add(room);
                    }
                    continue;
                }

                if (method.getName().equals("getOwnerName")) {
                    if (!room.getOwnerName().equalsIgnoreCase(strVal)) {
                        toRemove.add(room);
                    }
                    continue;
                }

                if (method.getName().equals("getName")) {
                    if (!room.getName().toLowerCase().contains(strVal.toLowerCase())) {
                        toRemove.add(room);
                    }
                    continue;
                }

                if (method.getName().equals("getGuildName")) {
                    if (!room.belongsToGuild()
                            || !room.getGuildName().toLowerCase().contains(strVal.toLowerCase())) {
                        toRemove.add(room);
                    }
                    continue;
                }

                if (method.getName().equals("filterAnything")) {
                    boolean matches = false;
                    if (room.getName().toLowerCase().contains(strVal.toLowerCase())) matches = true;
                    else if (room.getDescription().toLowerCase().contains(strVal.toLowerCase())) matches = true;
                    else if (room.getOwnerName().equalsIgnoreCase(strVal)) matches = true;
                    else if (room.belongsToGuild()
                            && room.getGuildName().toLowerCase().contains(strVal.toLowerCase())) matches = true;
                    else {
                        String tags = room.getTags();
                        if (tags != null && !tags.isEmpty()) {
                            for (String t : tags.split(";")) {
                                if (t.trim().equalsIgnoreCase(strVal)) {
                                    matches = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!matches) {
                        toRemove.add(room);
                    }
                    continue;
                }

                Object o = method.invoke(room);
                if (o instanceof String) {
                    NavigatorFilterComparator comparator =
                            Emulator.getGameEnvironment().getNavigatorManager().comperatorForField(method);
                    if (comparator != null) {
                        if (!this.applies(comparator, (String) o, strVal)) {
                            toRemove.add(room);
                        }
                    } else if (!((String) o).toLowerCase().contains(strVal.toLowerCase())) {
                        toRemove.add(room);
                    }
                } else if (o instanceof String[]) {
                    NavigatorFilterComparator comparator =
                            Emulator.getGameEnvironment().getNavigatorManager().comperatorForField(method);
                    boolean matchesAny = false;
                    for (String s : (String[]) o) {
                        if (s != null && comparator != null && this.applies(comparator, s, strVal)) {
                            matchesAny = true;
                            break;
                        }
                    }
                    if (!matchesAny) {
                        toRemove.add(room);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
        }

        result.removeAll(toRemove);
        toRemove.clear();
    }

    public abstract List<SearchResultList> getResult(Habbo habbo);

    public List<SearchResultList> getResult(
            Habbo habbo, NavigatorFilterField filterField, String value, int roomCategory) {
        return this.getResult(habbo);
    }

    private boolean applies(NavigatorFilterComparator comparator, String o, String value) {
        switch (comparator) {
            case CONTAINS:
                if (StringUtils.containsIgnoreCase(o, value)) {
                    return true;
                }
                break;

            case EQUALS:
                if (o.equals(value)) {
                    return true;
                }
                break;

            case EQUALS_IGNORE_CASE:
                if (o.equalsIgnoreCase(value)) {
                    return true;
                }
        }

        return false;
    }
}
