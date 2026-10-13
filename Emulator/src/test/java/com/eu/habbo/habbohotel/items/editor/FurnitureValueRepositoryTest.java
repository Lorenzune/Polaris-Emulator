package com.eu.habbo.habbohotel.items.editor;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FurnitureValueRepositoryTest {
    @Test
    void publicLookupBindsSpriteAndTypeAndReadsRareValue() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet set = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(set);
        when(set.next()).thenReturn(true);
        when(set.getBigDecimal("catalog_price")).thenReturn(new BigDecimal("100.00"));
        when(set.getInt("value_type")).thenReturn(5);
        when(set.getInt("is_rare")).thenReturn(1);
        when(set.getInt("id")).thenReturn(2000032719);
        when(set.getString("currency_label")).thenReturn("Diamanti");
        when(set.getBoolean("is_rare")).thenReturn(true);
        var value = FurnitureValueRepository.read(set);
        assertEquals(new BigDecimal("100.00"), value.get("value"));
        assertEquals(5, value.get("valueType"));
        assertEquals(true, value.get("isRare"));

    }

    @Test
    void optionalColumnsDoNotBreakLegacyEditorSchemas() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getBigDecimal("catalog_price")).thenThrow(new SQLException("Column missing"));
        assertEquals(FurnitureValueRepository.empty(), FurnitureValueRepository.read(set));
    }
}
