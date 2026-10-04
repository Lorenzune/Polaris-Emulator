package com.eu.habbo.habbohotel.mentions;
import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.sql.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MentionMuteRepositoryTest {
    @Test void rejectsEmptyOrOversizedNamesWithoutDatabaseAccess() throws Exception {
        assertFalse(new MentionMuteRepository(mock(javax.sql.DataSource.class)).update(7, " ", true).isEmpty());
        assertFalse(new MentionMuteRepository(mock(javax.sql.DataSource.class)).update(7, "x".repeat(65), true).isEmpty());
    }
    @Test void persistsOnlyForAuthenticatedOwnerAndResolvesNameToStableId() throws Exception {
        Database db = mock(Database.class, RETURNS_DEEP_STUBS);
        Connection connection = mock(Connection.class);
        PreparedStatement owner = mock(PreparedStatement.class), target = mock(PreparedStatement.class), count = mock(PreparedStatement.class), insert = mock(PreparedStatement.class);
        ResultSet ownerRows = mock(ResultSet.class), targetRows = mock(ResultSet.class), countRows = mock(ResultSet.class);
        when(db.getDataSource().getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")).thenReturn(owner);
        when(connection.prepareStatement("SELECT id FROM users WHERE username = ? LIMIT 1")).thenReturn(target);
        when(connection.prepareStatement("SELECT COUNT(*) FROM user_mention_mutes WHERE user_id = ?")).thenReturn(count);
        when(connection.prepareStatement("INSERT IGNORE INTO user_mention_mutes (user_id, muted_user_id) VALUES (?, ?)")).thenReturn(insert);
        when(owner.executeQuery()).thenReturn(ownerRows); when(ownerRows.next()).thenReturn(true);
        when(target.executeQuery()).thenReturn(targetRows); when(targetRows.next()).thenReturn(true); when(targetRows.getInt(1)).thenReturn(42);
        when(count.executeQuery()).thenReturn(countRows); when(countRows.getInt(1)).thenReturn(0);
        {
            var repository = new MentionMuteRepository(db.getDataSource());
            assertEquals("", repository.update(7, " @Bob ", true));
            verify(owner).setInt(1, 7);
            verify(target).setString(1, "Bob");
            verify(insert).setInt(1, 7); verify(insert).setInt(2, 42);
            verify(connection).commit();
        }
    }
}
