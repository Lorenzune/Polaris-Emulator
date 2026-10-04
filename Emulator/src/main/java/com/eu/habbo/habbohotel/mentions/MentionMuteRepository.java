package com.eu.habbo.habbohotel.mentions;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Account-owned mention preferences. IDs survive nickname changes. */
public final class MentionMuteRepository {
    private final DataSource dataSource;
    public MentionMuteRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }
    public List<String> list(int ownerId) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection c = this.dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT u.username FROM user_mention_mutes m JOIN users u ON u.id = m.muted_user_id WHERE m.user_id = ? ORDER BY u.username")) {
            s.setInt(1, ownerId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) names.add(r.getString(1)); }
        }
        return names;
    }

    public Set<Integer> mutedRecipients(int senderId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (Connection c = this.dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT user_id FROM user_mention_mutes WHERE muted_user_id = ?")) {
            s.setInt(1, senderId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) ids.add(r.getInt(1)); }
        }
        return ids;
    }

    public String update(int ownerId, String nickname, boolean add) throws SQLException {
        String name = nickname == null ? "" : nickname.trim();
        if (name.startsWith("@")) name = name.substring(1);
        if (name.isEmpty() || name.length() > 64) return "Nickname non valido.";
        try (Connection c = this.dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Serialize modifications for this account, including the list limit.
                try (PreparedStatement s = c.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    s.setInt(1, ownerId); try (ResultSet r = s.executeQuery()) { if (!r.next()) return "Account non disponibile."; }
                }
                int mutedId;
                try (PreparedStatement s = c.prepareStatement("SELECT id FROM users WHERE username = ? LIMIT 1")) {
                    s.setString(1, name);
                    try (ResultSet r = s.executeQuery()) { if (!r.next()) return "Utente non trovato."; mutedId = r.getInt(1); }
                }
                if (mutedId == ownerId) return "Non puoi silenziare te stesso.";
                if (add) {
                    try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM user_mention_mutes WHERE user_id = ?")) {
                        s.setInt(1, ownerId); try (ResultSet r = s.executeQuery()) { r.next(); if (r.getInt(1) >= 100) return "Puoi silenziare al massimo 100 utenti."; }
                    }
                }
                String sql = add ? "INSERT IGNORE INTO user_mention_mutes (user_id, muted_user_id) VALUES (?, ?)"
                                 : "DELETE FROM user_mention_mutes WHERE user_id = ? AND muted_user_id = ?";
                try (PreparedStatement s = c.prepareStatement(sql)) { s.setInt(1, ownerId); s.setInt(2, mutedId); s.executeUpdate(); }
                c.commit();
                return "";
            } finally { c.rollback(); }
        }
    }

    public Integer findFollowSender(int ownerId, int mentionId) throws SQLException {
        try (Connection c = this.dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT sender_user_id FROM habbo_mentions h WHERE h.id = ? AND h.target_user_id = ? AND NOT EXISTS (SELECT 1 FROM user_mention_mutes m WHERE m.user_id = h.target_user_id AND m.muted_user_id = h.sender_user_id)")) {
            s.setInt(1, mentionId); s.setInt(2, ownerId);
            try (ResultSet rows = s.executeQuery()) { return rows.next() ? rows.getInt(1) : null; }
        }
    }
}
