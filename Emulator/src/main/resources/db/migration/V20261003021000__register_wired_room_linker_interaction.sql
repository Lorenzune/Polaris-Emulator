-- Register paired Wired room endpoints. Repairs NULL or incorrect mappings.
UPDATE `items_base`
SET `interaction_type` = `item_name`
WHERE `item_name` = 'wf_room_linker'
  AND NOT (`interaction_type` <=> `item_name`);
