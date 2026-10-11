-- Register the chooser controller without changing furniture IDs or room states.
UPDATE `items_base`
SET `interaction_type` = 'conf_chooser_disable'
WHERE `item_name` = 'conf_chooser_disable'
  AND NOT (`interaction_type` <=> 'conf_chooser_disable');
