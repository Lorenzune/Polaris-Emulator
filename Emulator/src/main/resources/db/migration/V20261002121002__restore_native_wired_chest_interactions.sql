-- All original Wired storage variants share the native currency/furni chest handlers.
-- Repair older/default/NULL interaction mappings without touching stored contents.
UPDATE `items_base`
SET `interaction_type` = `item_name`
WHERE `item_name` IN ('wf_storage_coins1', 'wf_storage_coins2',
                     'wf_storage_furni1', 'wf_storage_furni2', 'wf_storage_furni_starter')
  AND NOT (`interaction_type` <=> `item_name`);
