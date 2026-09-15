ALTER TABLE rooms
    ADD COLUMN navigator_order INT NOT NULL DEFAULT 0 AFTER owner_name;
