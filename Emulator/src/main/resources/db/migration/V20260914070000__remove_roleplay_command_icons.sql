-- Roleplay commands use text labels only; emoji icons are not part of the feature.
ALTER TABLE `roleplay_commands`
    DROP COLUMN IF EXISTS `icon`;
