-- Register the original projectile furniture with its matching Wired interaction.
-- Data-only and idempotent; also repairs a NULL interaction_type.
UPDATE `items_base`
SET `interaction_type` = 'wf_xtra_rotate_to_dir'
WHERE `item_name` = 'wf_xtra_rotate_to_dir'
  AND NOT (`interaction_type` <=> 'wf_xtra_rotate_to_dir');
