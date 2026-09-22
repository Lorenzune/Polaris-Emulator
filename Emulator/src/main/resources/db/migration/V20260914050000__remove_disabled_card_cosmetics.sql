-- Card backgrounds are no longer part of the profile cosmetics feature.
DELETE FROM custom_profile_cosmetics_catalog WHERE LOWER(category) IN ('card', 'cards');
DELETE FROM custom_profile_cosmetic_categories WHERE LOWER(category_type) IN ('card', 'cards');
DELETE FROM users_profile_cosmetics WHERE LOWER(category) IN ('card', 'cards');

-- Keep legacy columns for packet/database compatibility, but clear their values.
UPDATE users SET background_card_id = 0;
UPDATE bots SET background_card_id = 0;
