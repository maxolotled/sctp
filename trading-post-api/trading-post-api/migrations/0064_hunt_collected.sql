-- A finisher opened a chest at their prize (the mod saw it): its gold mark and the notes on screens go away
ALTER TABLE huntPlayers ADD COLUMN collectedAt TEXT;
