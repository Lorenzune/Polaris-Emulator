-- Recycler boxes carry a creation date in extra_data and are opened through
-- OpenRecycleBoxEvent. They must never be instantiated as crackable furni.
UPDATE `items_base`
SET `interaction_type` = 'default'
WHERE `item_name` = 'ecotron_box';
