package com.eu.habbo.habbohotel.toolbar;

import com.eu.habbo.database.SqlQueries;

/** A singleton row with a compare-and-set revision prevents one operator overwriting another. */
public final class ToolbarConfigurationStore {
    public record State(int revision, ToolbarConfiguration configuration) {}

    private ToolbarConfigurationStore() {}

    public static State load() {
        return SqlQueries.queryOne("SELECT revision, configuration FROM toolbar_configuration WHERE id=1", row -> {
                    String json = row.getString("configuration");
                    return new State(row.getInt("revision"), json == null ? null : ToolbarConfiguration.parse(json));
                })
                .orElseThrow(() -> new IllegalStateException("Toolbar configuration migration missing"));
    }

    public static boolean save(int revision, ToolbarConfiguration configuration, int actorId) {
        configuration.validate();
        return revision >= 0
                && revision < Integer.MAX_VALUE
                && SqlQueries.update(
                                "UPDATE toolbar_configuration SET configuration=?, revision=revision+1, updated_by=?, updated_at=UNIX_TIMESTAMP() WHERE id=1 AND revision=?",
                                ToolbarConfiguration.JSON.toJson(configuration),
                                actorId,
                                revision)
                        == 1;
    }
}
