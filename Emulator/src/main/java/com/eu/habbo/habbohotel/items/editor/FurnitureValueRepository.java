package com.eu.habbo.habbohotel.items.editor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.Map;

/** Optional public furniture metadata; no changes to the established furniture packets. */
public final class FurnitureValueRepository {
    private final DataSource dataSource;

    public FurnitureValueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> find(int spriteId, String type) throws SQLException {
        return new com.eu.habbo.habbohotel.catalog.RareEconomyRepository(dataSource).find(spriteId,type);
    }

    public static Map<String, Object> read(ResultSet set) {
        try {
            BigDecimal value = set.getBigDecimal("catalog_price");
            int type = set.getInt("value_type");
            return Map.of("value", value == null ? BigDecimal.ZERO : value,
                    "catalogPrice",value == null ? BigDecimal.ZERO : value,
                    "valueType", type, "isRare", set.getBoolean("is_rare"));
        } catch (SQLException missingOptionalColumns) {
            return empty();
        }
    }

    public static Map<String, Object> empty() {
        return Map.of("value", BigDecimal.ZERO, "catalogPrice",BigDecimal.ZERO,"valueType", -1, "isRare", false);
    }
}
