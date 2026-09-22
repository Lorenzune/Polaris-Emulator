-- Register and map missing official SWF Wired interactions in items_base
UPDATE items_base SET interaction_type = 'wf_xtra_projectile'
    WHERE item_name = 'wf_xtra_projectile' AND interaction_type <> 'wf_xtra_projectile';

UPDATE items_base SET interaction_type = 'wf_xtra_jump_strength'
    WHERE item_name = 'wf_xtra_jump_strength' AND interaction_type <> 'wf_xtra_jump_strength';

UPDATE items_base SET interaction_type = 'wf_xtra_achievement_enabler'
    WHERE item_name = 'wf_xtra_achievement_enabler' AND interaction_type <> 'wf_xtra_achievement_enabler';

UPDATE items_base SET interaction_type = 'wf_xtra_global_placeholder'
    WHERE item_name = 'wf_xtra_global_placeholder' AND interaction_type <> 'wf_xtra_global_placeholder';

UPDATE items_base SET interaction_type = 'wf_act_progress_reward_track'
    WHERE item_name = 'wf_act_progress_reward_track' AND interaction_type <> 'wf_act_progress_reward_track';

UPDATE items_base SET interaction_type = 'wf_act_progress_achievement'
    WHERE item_name = 'wf_act_progress_achievement' AND interaction_type <> 'wf_act_progress_achievement';

UPDATE items_base SET interaction_type = 'wf_act_reset_reward_track'
    WHERE item_name = 'wf_act_reset_reward_track' AND interaction_type <> 'wf_act_reset_reward_track';

UPDATE items_base SET interaction_type = 'wf_act_override_height'
    WHERE item_name = 'wf_act_override_height' AND interaction_type <> 'wf_act_override_height';

UPDATE items_base SET interaction_type = 'wf_act_click_settings'
    WHERE item_name = 'wf_act_click_settings' AND interaction_type <> 'wf_act_click_settings';

UPDATE items_base SET interaction_type = 'wf_cnd_user_level'
    WHERE item_name = 'wf_cnd_user_level' AND interaction_type <> 'wf_cnd_user_level';

UPDATE items_base SET interaction_type = 'wf_act_write_to_logs'
    WHERE item_name = 'wf_act_write_to_logs' AND interaction_type <> 'wf_act_write_to_logs';

UPDATE items_base SET interaction_type = 'wf_act_neg_write_to_logs'
    WHERE item_name = 'wf_act_neg_write_to_logs' AND interaction_type <> 'wf_act_neg_write_to_logs';
