-- Register the room teleport effect with its matching interaction handler.
-- Data-only and idempotent; also repairs NULL interaction mappings.
UPDATE `items_base`
SET `interaction_type` = `item_name`
WHERE `item_name` = 'wf_act_teleport_to_room'
  AND NOT (`interaction_type` <=> `item_name`);
