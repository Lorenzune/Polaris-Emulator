-- Furniture reports store the complete items_base snapshot and proposed values.
-- The legacy 500-character issue column is too small for that payload.
ALTER TABLE support_tickets DROP INDEX issue;
ALTER TABLE support_tickets MODIFY issue TEXT NOT NULL;
CREATE INDEX issue ON support_tickets (issue(191));
