-- Adapted from Polaris 2afde110. Add only missing rows; preserve hotel custom data.
-- Checked against the local pet bundles. No reliance on a UNIQUE key for replay safety.
INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 8, 12, 12, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=8 AND `color_one`=12 AND `color_two`=12 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 8, 13, 13, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=8 AND `color_one`=13 AND `color_two`=13 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 8, 15, 15, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=8 AND `color_one`=15 AND `color_two`=15 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 8, 16, 16, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=8 AND `color_one`=16 AND `color_two`=16 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 8, 17, 17, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=8 AND `color_one`=17 AND `color_two`=17 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 9, 9, 9, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=9 AND `color_one`=9 AND `color_two`=9 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 9, 10, 10, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=9 AND `color_one`=10 AND `color_two`=10 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 9, 11, 11, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=9 AND `color_one`=11 AND `color_two`=11 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 11, 7, 7, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=11 AND `color_one`=7 AND `color_two`=7 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 11, 14, 14, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=11 AND `color_one`=14 AND `color_two`=14 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 11, 16, 16, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=11 AND `color_one`=16 AND `color_two`=16 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 11, 17, 17, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=11 AND `color_one`=17 AND `color_two`=17 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 14, 14, 14, '1', '1'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=14 AND `color_one`=14 AND `color_two`=14 AND `has_color_one`='1' AND `has_color_two`='1');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 26, 1, 1, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=26 AND `color_one`=1 AND `color_two`=1 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 26, 6, 6, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=26 AND `color_one`=6 AND `color_two`=6 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 26, 7, 7, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=26 AND `color_one`=7 AND `color_two`=7 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 26, 28, 28, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=26 AND `color_one`=28 AND `color_two`=28 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 26, 29, 29, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=26 AND `color_one`=29 AND `color_two`=29 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 26, 30, 30, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=26 AND `color_one`=30 AND `color_two`=30 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 27, 1, 1, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=27 AND `color_one`=1 AND `color_two`=1 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 27, 6, 6, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=27 AND `color_one`=6 AND `color_two`=6 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 27, 7, 7, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=27 AND `color_one`=7 AND `color_two`=7 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 27, 28, 28, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=27 AND `color_one`=28 AND `color_two`=28 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 27, 29, 29, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=27 AND `color_one`=29 AND `color_two`=29 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 27, 30, 30, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=27 AND `color_one`=30 AND `color_two`=30 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 35, 19, 19, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=35 AND `color_one`=19 AND `color_two`=19 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 35, 29, 29, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=35 AND `color_one`=29 AND `color_two`=29 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 35, 31, 31, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=35 AND `color_one`=31 AND `color_two`=31 AND `has_color_one`='1' AND `has_color_two`='0');

INSERT INTO `pet_breeds` (`race`, `color_one`, `color_two`, `has_color_one`, `has_color_two`)
SELECT 35, 32, 32, '1', '0'
WHERE NOT EXISTS (SELECT 1 FROM `pet_breeds` WHERE `race`=35 AND `color_one`=32 AND `color_two`=32 AND `has_color_one`='1' AND `has_color_two`='0');
