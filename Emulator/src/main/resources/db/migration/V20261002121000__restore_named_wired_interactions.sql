-- Restore each named Wired furniture definition to its matching interaction.
-- Data-only and idempotent; other furniture definitions are unaffected.
UPDATE `items_base`
SET `interaction_type` = `item_name`
WHERE `item_name` IN (
    'wf_act_click_conf',
    'wf_act_neg_log',
    'wf_act_log',
    'wf_xtra_mov_curve'
)
AND NOT (`interaction_type` <=> `item_name`);
