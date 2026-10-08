# Changelog

## 2.4 (mod 2.4)

### Mod
- **Rare-dle in-game:** a new Rare-dle tab (and dashboard tile) plays today's daily Rare-dle. It's the same puzzle, account, streak and leaderboard as sctp.nl/rare-dle. There's no password: the mod plays on the sctp.nl account linked to your Minecraft name, with a sign-in that only works for Rare-dle. Guesses show the same coloured hints, the pixel hint sharpens as you go, and the answer shows when you're done.
- **Empty hand for block clicks:** a new Settings › Empty hand page. Press Add, hold an item, press X, and from then on clicking doors, chests, buttons, levers, crafting tables and every other clickable block uses an empty hotbar slot (or a plain vanilla item) instead of that item, so its own right-click doesn't fire. Works everywhere, for as many items as you like. Items match exactly (same item and same name), so your named sword doesn't affect plain swords. Sneak-clicking still uses the item, and the page lists your items with a Remove button.
- **Double chest shops:** double chests are scanned now, as one shop. The sign on the left half (as you face the chest) is the price; if only the right half has a sign, that one counts.
- **Highlights that show through walls:** your own shop chests with a payment waiting get a bright green outline, visible through blocks, instead of green particles. The teleport beam's "Glowing chest outline" style is now this same outline ("Chest highlight (through walls)").
- **Beacon beam teleport style:** "Destination marker only" is now "Beacon beam at the chest": a real beacon beam rising out of the shop you're heading to.
- **Empty chests get grey markers:** a recently scanned shop with nothing for sale gets a grey particle on its sign instead of red.
- **Ender chest ⇄ backpack:** the two arrows are now one icon button on the left that opens whichever one you're not in (a bundle for your backpack, an ender chest for /ec).
- **Stock holograms** (off by default, Settings › Look & tools): a small line on the front of each scanned shop chest, under its sign: "64 in stock · 3m ago" or "Empty". It sits flat on the chest like sign text (it doesn't turn to follow you), only shows within 8 blocks, and only when you're in front of the chest.
- **Real sales:** every scan now also reports how much payment is sitting in the shop, so the website can tell real sales apart from sellers taking stock out (see Website).
- **Popups instead of chat messages:** the welcome message for new players and "update available" now show as a small popup on your screen with a button (walkthrough or download) and an X, instead of a chat line that scrolls away. "Shop Logger updated" stays a chat message. They wait until you're in the world with no menu open.
- **Hide display listings:** a "Display: Shown / Hidden" chip on the Listings screen and a Settings › Look & tools option leave out [DISPLAY] shops everywhere: listings, item pages, seller profiles and chat `/search`.

### Website
- **Rare auctions (/auction):** enter rares for the next auction in a quick table: search a rare and it's added straight away, with SCTP's value and suggested prices filled in. Supports Dutch clock auctions (the price keeps dropping until someone claims it) and regular auctions. You can tick "Let the host pick" instead of setting a price. You get your shulker name ("Registered <name>") and the drop-off location for that auction. **My Auctions** shows your results and payouts, and the homepage shows the next auction (date, host, drop-off, spots left) with a button to enter.
- **Wishlist:** star (☆) the rares and mapart you want on My Collection, per world. Owning an item takes its star away. A wishlist card with a "Copy wishlist link" button shares it (`/collection/<name>#wishlist` opens straight on it), the Have / Need filter has a Wishlist option, and "Share what I still need" can make a wishlist picture.
- **Shop Logger on Modrinth:** the download buttons point to Modrinth (biggest) and CurseForge, with the direct download still available.
- **Real sales in every statistic:** "sold" used to be any stock that disappeared, so restocking, moving items or delisting all counted as sales. Now an item only counts as sold when a shop's stock went down **and** its payment showed up in that chest between two scans (by anyone with mod 2.4). Item pages, the Statistics page (best-selling), My Shop Statistics and the restock hints all use this, counted from 7 October 2026. A sale where the seller collects the payment before the next scan can be missed, so the numbers can only be a little low, never inflated.
- **Other languages:** item names uploaded from a game set to another language (German, French…) are turned back into the English names, so they match everyone else's.
- **20 new rares** from the item sheet (The Grimoire, the organ collectibles, the Star (Reversed), new plushies…), with updated details for existing ones.

### Admin
- **Old versions cut off:** uploads from mod versions below 2.2.1 are rejected (those could log ghost items), and the archived Minecraft 1.21.11 / 26.1 downloads are gone from the site.
- **Auction admin (/auction/admin, "auctions" permission):** create auctions (type, host, drop-off location, date, limits, cut %), see every entry per player, record what sold, and add items for a player who handed in a shulker without using the site.
- **Listing bans:** "Ban" next to "Rem." on listings and in reports hides that item from that seller for good; the Banned listings section in the admin page undoes it.

### Backend
- Migration 0050: `containerScans` (last scan per chest: stock per listing + payment), `shopSales` (every confirmed sale) and `confirmedSold` / `confirmedRevenueDiamonds` on `sellerItemDailyStats`. Uploads accept `payment`, `paymentCurrency` and `scannedAt` per scanned position; `/stats/item`, `/stats/world` and `/stats/mine` return confirmed sales (`totalSold`, `recentSold`…) and `salesSince`.
- Migrations 0040 (item name translations), 0041–0044 and 0046–0047 (auctions, cut %, type, host, "host picks", drop-off), 0045 (`bannedListings`), 0048 (`wishlistItems`).
- New endpoints: `/auction/*` and `/admin/auctions/*` (incl. `add-items`), `/admin/listings/ban|bans|unban`, `POST /mod/login` (in-game Rare-dle sign-in by Minecraft name; the session only works on `/raredle/*`, migration 0051 adds `adminSessions.scope`), and `list: "wish"` on `/collection/set`.

## 2.3 (mod 2.3)

### Mod
- **All-new menus.** Every Shop Logger screen was redesigned to match the website: dark green cards, lime accents, proper switches, rounded buttons and filter chips, framed search boxes, and styled lists with a slim scrollbar. Every feature from before is still there.
- **Easier to get around:** a top bar on every screen with Back, a "Trading Post › page" breadcrumb, and icon tabs to jump straight to Listings, Items, Rares, Watchlist, Marketplace, Mapart or Settings.
- **Home is now a dashboard:** status at a glance (world, scanner, shops nearby, watchlist), a search box that goes straight to the listings (press Enter), and a tile per section with live badges.
- **One Settings screen:** Settings and Advanced settings are merged into categories (Scanning, Alerts, Look & tools, Data, Advanced). Every option has a one-line explanation. The rescan cooldown is now a − / + stepper, and Export / Upload confirm with "Done ✓".
- **Item pages have tabs:** Listings (with a count), Price history (both charts side by side) and Details for rares. The header shows a big icon and the key facts.
- **Listings** show item icons, a coloured world dot, and rounded TP / Report buttons with tooltips.
- **Watchlist:** "Watching" and "+ Add" badges, a clearer options card with the price shown in diamonds too, and your search and scroll position are kept.
- **Marketplace:** "Selling" / "Looking for" badges and a button to post on sctp.nl. **Seller profiles** show the item name on each listing and link to the profile on sctp.nl.
- **Mapart scanner:** proper on/off switch, stats at a glance, cards with coloured upload status. It's now **off by default for everyone** (uploads are paused server-side), including players who had it on, and the screen shows "Enabling this feature will capture all maps in item frames visible to you."
- **Colour themes:** pick one of 10 themes for every Shop Logger screen in Settings › Look & tools: Snail (the default, matching the website), Ocean, Amethyst, Sakura, Honey, Pumpkin, Crimson, Frost, Firefly and Graphite. The ‹ › arrows preview them live; clicking the name opens the full list. Status colours (warnings, errors) and world colours stay the same in every theme.
- **Filters open a pick list:** clicking any filter chip (Listings, Items, Rares, Item pages, Marketplace) opens a popup with every option, the current one highlighted, and a search box for long lists. Shift+click still steps to the next option.
- **Watchlist from item pages:** every item page has "+ Add to watchlist" (adds it and opens its options) or "Watchlist settings" next to its tabs.
- **Items / Rares keep your place:** going back from an item page returns the list to where you were scrolled.
- **In-game marketplace filters:** World (Firefly, Honeybee or both; starts on your current world, cross-world posts show under either) and Show (all, Selling, Looking for).
- **Glowing chest outline:** a new teleport highlight style that outlines the shop's chest instead of drawing a beam toward it.
- **No more cut-off names on the dashboard:** tile badges ("Scanning off", "12 watched") shrink or move down instead of squeezing the tile's name, and Settings rows show their full text on hover when it doesn't fit.
- **Ender chest ⇄ backpack:** arrows on either side of your ender chest and backpack screens: left opens your backpack (`/bp`), right your ender chest (`/ec`); the one for the screen you're on is greyed out. They can be turned off in Settings › Look & tools.
- **Rare items: filter by release date.** The "Dyeable" filter on the Rares tab is replaced by "Released": All, a year (2025, 2026) or a single month (Jan 2025 … Sep 2026), oldest first. Items released in two periods show up under both.
- **Scans never use the item in your hand.** The auto-scanner opens a chest by right-clicking it, and that right-click used to carry whatever you were holding, so server items that react to any right-click could fire on every shop you walked past. It now clicks with an empty hotbar slot, or failing that a plain vanilla item, and only uses your current item if neither exists. It also pauses while you sneak, since a sneaking right-click uses the item instead of opening the chest.
- **Fixed chests popping up on screen while scanning** with "Shop info on visit" on: the delayed `/shops plot info` command could release the chest being scanned at that moment. The command now waits until no scan is in flight.
- **Rares show their own texture in listings** (Listings, item pages, seller profiles) instead of the vanilla item they're made from.
- **Fixed scans reporting an old price.** The auto-scanner remembered each shop's sign from when it first found the shop, so after a seller changed the price (say 25db → 20db) scans kept using the old one, both in watchlist alerts and in what was uploaded. The sign is now re-read right before every scan and kept up to date in the background.

### Website
- **Written job reviews:** besides 👍 / 👎 you can now write a review (up to 500 characters) on someone's job post. Reviews show in the job's detail view, and you can edit or remove your own.
- **Mapart likes:** a heart on every mapart (catalog, mapart pages and the home page's mapart of the day), one like per account, and a "Most liked" sort in the catalog.
- **Fixed "Mine" on the marketplace** saying you needed to log in while you were logged in (when the page was opened straight on the Mine tab).
- **Rare release dates cleaned up:** every rare now has one plain month, so none go missing from the month filters. Summerfest items count as Aug 2026, crate keys only show their original month (not the birthday-crate re-release), "- Present" was dropped, and the Spawner and Quest Crate Keys are Jan 2025. Rares with no date stay under "All".
- **Fixed Rare-dle's release date hint** for rares released in a season ("Summer 2026"): it now shows older / more recent again.
- **Renew listings and job posts:** a "Renew 14 days" button under your own posts in "Mine" pushes the expiry date 14 days out, so a post that's still relevant doesn't have to be posted again. It also brings back a post that just expired. Your posts now show when they expire.

### Backend
- Migration 0038: `comment` column on job reviews. New endpoints `GET /marketplace/jobs/reviews`, `POST /marketplace/jobs/renew` and `POST /marketplace/listings/renew`.
- Migration 0039: `mapartLikes`. New endpoints `POST /mapart/like` and `GET /mapart/my-likes`; every public mapart response includes `likes`.

## 2.2.1 (mod 2.2.1, September 2026)

### Mod
- **Fixed shop items sometimes being logged at the wrong chest.** The auto-scanner matched each silently opened chest to "whatever container screen arrives next", so if something else opened at the same moment, its contents could be recorded as that shop's stock. That could be your own chest, a crafting table, a villager, an NPC or a command menu (/ah, /menu...), or a late reply to an earlier scan. Now:
  - A screen is only accepted if it looks like a shop chest (a plain 3-row chest menu). Anything else is shown to you normally and never logged.
  - Right-clicking a block with a menu, an entity, or a custom-named item, or running a command, pauses the scanner until your own screen has had time to open.
  - After a scan gets interrupted or times out, the scanner waits before trying again, so a late reply can't land on the next chest.
- **Double chests are never scanned** (automatically or when you open one yourself). Shops can't be double chests, so anything in one was never a real listing. Anything previously logged for a double chest gets cleared.

## 2.2 (mod 2.2, September 2026)

### Mod
- **Fixed a scan-suppression bug:** the auto-scanner silently reads a shop chest's contents by briefly opening its screen off-screen — a safeguard meant to protect your own ender chest from getting caught in that (showing nothing, and its contents possibly logged as if they belonged to the shop being scanned) existed in the code but was never actually switched on. It's genuinely active now.
- **Adaptive scan-wait timing:** the pause between the auto-scanner's silent opens no longer uses one fixed number — it now sizes itself from how long recent opens actually took to resolve, so it stays snappy on a good connection but automatically backs off during lag instead of racing a slow-to-resolve manual open elsewhere.
- **Temporary:** an Advanced Settings toggle ("Show scan wait (temp)") displays the current adaptive wait time, in ms, in the top-right corner — off by default, here only to help verify the above during the 2.2 accuracy-check period, will be removed once that's done.
- **`/preview` formatting codes:** `/preview` now understands legacy formatting codes for **bold**, *italic*, ~~strikethrough~~, obfuscated and underlined text, so names generated by tools like Birdflop preview correctly instead of showing raw code characters (thanks ZyskiDev).

### Website
- **Mapart keywords:** artists can tag each mapart with up to 5 keywords. They show as `#tags` on the gallery card and the mapart's page (click one to find everything with that tag), and the gallery search matches them.
- **Mapart type:** each piece can be marked as a **flat** or **staircased** map, with a filter for it in the gallery.
- **New mapart category: Games.**
- **Bigger collabs:** a mapart can now list up to 16 artists.
- **Split wrongly-merged mapart** (admin): a stitched-together piece can be cut back into its individual maps, each keeping its own name and picture.

## 2.1 (mod 2.1, September 2026)

### Mod
- **`/preview <name>`:** live-preview a colored/styled rename on the item in your hand before you actually commit to it — type the name with `&#RRGGBB` hex color blocks (e.g. `/preview &#FF0000Cool &#00FF00Sword`) and it's applied to the held item's display name instantly, client-side only. Move or drop the item to clear the preview; nothing is actually renamed server-side.

## 2.0 (mod 2.0 + website, September 2026)

Everything since mod 1.6 (released 14 Sep 2026).

### Mod
- **Four builds now ship:** Fabric 26.2, Fabric 26.3, NeoForge 26.2 and NeoForge 26.3. Fabric 1.21.11 and 26.1 stay unsupported (dropped in 1.6). Each Fabric jar now refuses to load in the wrong Minecraft version instead of crashing on startup.
- **Mapart scanner (new, built in):** finds item-frame mapart near you, merges walls of maps into full pieces and uploads them to the mapart gallery.
  - Stitching, PNG encoding and uploading all run on one low-priority background thread — the game thread only reads frames and hashes their pixels, so it never freezes.
  - Nothing is ever printed to chat and there is no `/mapartworld` command: the world comes from the mod's own world detection, and it only scans once the world has been confirmed on the current connection.
  - A "Mapart Scanner" button on the X-menu shows what it found (thumbnails and upload status), with an on/off switch and "Re-send nearby". Scanning defaults to ON.
- **Advanced Settings screen:** chat scan log format (single/multiple lines), per-shop scan cooldown, teleport beam style, whether `/search` opens the GUI or chat, and "shop info on visit" (sends `/shops plot info` at most once an hour per shop).
- **Settings screen** reorganised and simplified.
- **Watchlist:** quick price buttons (1, 5, 10, 15, 20, 25, 32, 48 blocks) in item options; a "Search" button per row that jumps to the item's detail page; the watchlist screen keeps your search text and scroll position when you return from another screen; alerts now include marketplace listings with a clickable link to the listing on the website.
- **In-game reporting:** a red [Report] button on watchlist alerts and in the listings list — one click, once per listing per session.
- **Ignore this listing:** a grey [Ignore] button on watchlist alerts (shop listings and marketplace posts) stops that exact listing from alerting you again — the item stays on your watchlist and other sellers still alert. `/watchunignore` forgets everything you ignored.
- **Rare rentals:** optional Advanced setting (off by default) to also highlight rentable rares inside opened shulker boxes.
- **Auto-scanner:** no longer silently right-clicks while you hold a name tag or a feather (so it can't redeem fly tokens).
- Shop-visit alert fixes; proper names for music discs.

### Website — main table and search
- Hide any column with its × and bring it back from the "Columns ▾" menu (Report/Share always stay).
- Price filter and sort now work per single item in diamond-equivalent, with "≈ x dia each" under prices. A **"Convert currencies to diamond-equivalent"** switch (on by default, remembered) is on the main table, item pages, seller pages and the material-list searcher; off uses the raw price in each listing's own currency.
- "Did you mean…?" suggestions when a search finds 0–3 results.
- General price-check value per world on every rare item page.
- **Mapart of the day** on the home page.
- Redesigned navigation: More ▾ (Roadmap, Search by material list, World/Shop statistics) and Info ▾ (FAQ, Installation, Features going straight to those docs pages); the Rare-dle button is shown greyed out as "coming soon".

### Website — mapart
- Mapart gallery (`/mapart`) with a page per piece; owners claim, edit, abandon and upload pieces (with a crop tool) from the management page, and can request takedowns that a head admin approves.
- Automatic artist and title detection with nickname aliases; mapart also appears as gallery rows in the main listings table.
- **New fields:** optional price; **collab artists** (a dropdown arrow adds more artist fields — the first is the head artist and is shown first); **"Was this a commission?"** (artist becomes "built by" plus a "commissioned by" field). Commission info (open/closed, prices and details, Discord) is entered on the management page and shown on the artist's profile.
- **Search by image** (reverse image search) and near-duplicate detection on upload.
- **Reports and moderation:** anyone can report wrong artist, wrong world, wrong category or an inappropriate image from any mapart listing. Reports go into the existing reports queue; holders of the new `manageMapart` permission see and resolve them and can edit artist/world/category directly.
- New categories Seasonal and Advertisement; Fandom removed (its three pieces moved to Misc).

### Website — collections, profiles, games
- **Collections** (`/collection/`): tick off rare items and mapart per world, with full stats; public by default at `/collection/<username>` (private toggle). Ownership toggles on the item-library cards, the mapart catalog cards and both detail pages.
- **Public profiles:** the seller page (`/s/ff/<name>`) is now a tabbed profile — Shop, Mapart (with commission info), Collection, Marketplace. "My Profile" and "My Collection" in the account menu.
- **Rare-dle** (`/rare-dle/`, testing mode): a daily "guess the secret rare" game — login required, 8 tries, per-attribute feedback, a pixel hint of the rare's icon that sharpens from 2×2 (after 4 guesses) to 3×3, 4×4 and 8×8, points and streaks, a +125 "no peek" bonus for not opening the Rare Items pages during the game, and Today / All-time / Streak leaderboards. While in testing mode you can reset the daily game or play unlimited random practice rounds (practice never counts toward points or leaderboards).
- **Marketing studio (admin panel):** 13 ready-made templates (announcement, big statement, what's-new list, live "by the numbers", mapart and rare spotlights, mapart/rare collections, marketplace promo, mod release, Rare-dle teaser, event/giveaway, player shoutout, today's mapart) that open in the image maker, which can also download every size or every style in one go.
- **Home page:** a featured strip with a bigger mapart of the day, a slot reserved for Rare-dle, and a marketplace card with the live listing count.
- **Image maker:** shareable pictures in 13 formats (Discord, X, Instagram, story, YouTube, wallpaper, banners, custom…) and 12 styles with patterns, layouts and your own colours — for mapart of the day, mapart pages, rare item pages, profiles, the collection "still needed / owned" builder and Rare-dle results.

### Website — shops and marketplace
- **My Shop Statistics:** undercut alerts, "what to reprice" and "what to restock" hints.
- **Marketplace:** jobs board (post, express interest, close), replies to offers, listing ids with share links and deep links, nicer sharing, and a new-feature nav highlight.
- **Your store:** manual listings (up to 100) and store managers.
- **Downloads:** redesigned download popup with a loader and version picker, including NeoForge.
- Verification is now for mapart only, through single-use head-admin links; marketplace verification marks were removed.
- New registration helpline docs page; docs Features page updated for 2.0.
- Rare and vanilla catalogs: new 26.2 items, missing-item fixes, new rare textures; "Aquatica Crate Key" renamed "Aquatic Crate Key".

### Backend
- Migrations 0015–0028: marketplace jobs, listing ids, mapart (+ all-names, claim flags, uploads, takedowns), store managers, collections, mapart price, profiles/mapart-of-the-day/image-search, Rare-dle, Rare-dle practice, Rare-dle no-peek flag.
- New Worker endpoints for mapart reports and edits, collections, public profiles, commission info, mapart of the day, image search and its indexer, seller-shop stats hints, and Rare-dle.
- Admin panel: mapart of the day (re-roll and promo image), image index builder, mapart takedowns, verification links.
