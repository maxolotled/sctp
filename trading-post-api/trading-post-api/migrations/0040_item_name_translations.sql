-- Item and enchantment names in every Minecraft language -> the US English
-- names the site uses. Mod versions before the English-names fix uploaded
-- vanilla item names in the player's game language ("Truhe" for Chest); the
-- Worker looks incoming names up here on upload and stores the English one.
-- Filled by scripts/build-item-name-translations.js (re-run after a
-- Minecraft update). baseItem is the item id without "minecraft:", or
-- "*enchantment" for enchantment names (enchanted books).

CREATE TABLE itemNameTranslations (
	foreignName TEXT NOT NULL,
	baseItem TEXT NOT NULL,
	englishName TEXT NOT NULL,
	PRIMARY KEY (foreignName, baseItem)
) WITHOUT ROWID;
