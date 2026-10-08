-- Sessions can be limited to one part of the API. NULL = a normal login.
-- 'raredle' = the mod's in-game Rare-dle sign-in (POST /mod/login), which
-- trusts the Minecraft name the mod sends, so it may only touch /raredle/*.
ALTER TABLE adminSessions ADD COLUMN scope TEXT;
