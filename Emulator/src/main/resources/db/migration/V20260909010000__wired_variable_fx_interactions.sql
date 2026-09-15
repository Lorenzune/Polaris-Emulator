-- Register and map Wired Variable FX Add-ons interaction_type in items_base
UPDATE items_base SET interaction_type = 'wf_xtra_varfx_hp'
    WHERE item_name = 'wf_xtra_varfx_hp' AND interaction_type <> 'wf_xtra_varfx_hp';

UPDATE items_base SET interaction_type = 'wf_xtra_varfx_prog'
    WHERE item_name = 'wf_xtra_varfx_prog' AND interaction_type <> 'wf_xtra_varfx_prog';

UPDATE items_base SET interaction_type = 'wf_xtra_varfx_levelling'
    WHERE item_name = 'wf_xtra_varfx_levelling' AND interaction_type <> 'wf_xtra_varfx_levelling';

UPDATE items_base SET interaction_type = 'wf_xtra_varfx_status'
    WHERE item_name = 'wf_xtra_varfx_status' AND interaction_type <> 'wf_xtra_varfx_status';

UPDATE items_base SET interaction_type = 'wf_xtra_varfx_boss'
    WHERE item_name = 'wf_xtra_varfx_boss' AND interaction_type <> 'wf_xtra_varfx_boss';

UPDATE items_base SET interaction_type = 'wf_xtra_varfx_number'
    WHERE item_name = 'wf_xtra_varfx_number' AND interaction_type <> 'wf_xtra_varfx_number';
