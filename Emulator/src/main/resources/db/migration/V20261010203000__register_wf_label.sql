UPDATE items_base SET interaction_type = 'wf_label'
WHERE item_name = 'wf_label' AND NOT (interaction_type <=> 'wf_label');
