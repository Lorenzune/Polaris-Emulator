-- Register the native Wired transaction effects, triggers and contracts.
UPDATE items_base
SET interaction_type = item_name
WHERE item_name IN (
    'wf_act_init_transaction', 'wf_act_cancel_transaction',
    'wf_trg_transaction_fail', 'wf_trg_transaction_complete',
    'wf_xtra_custom_contract', 'wf_contract_trade',
    'wf_contract_reward', 'wf_contract_payment'
)
AND NOT (interaction_type <=> item_name);
