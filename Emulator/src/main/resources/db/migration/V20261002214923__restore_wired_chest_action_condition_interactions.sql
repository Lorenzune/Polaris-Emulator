-- Register chest conditions, rewards and the scanner with their matching handlers.
-- Data-only and idempotent; also repairs NULL interaction mappings.
UPDATE `items_base`
SET `interaction_type` = `item_name`
WHERE `item_name` IN (
    'wf_cnd_chest_has_items',
    'wf_cnd_chest_has_item_type',
    'wf_act_give_furni',
    'wf_act_give_currency',
    'wf_xtra_scan_chest_furni_by_type'
)
  AND NOT (`interaction_type` <=> `item_name`);
