# Snailcraft Trading Post API

A tiny Cloudflare Worker whose only job is: receive shop data from the mod,
merge it, and commit it to `data/listings.json` in your GitHub repo. GitHub
Pages then serves that file as a normal static asset — that's your
"database," living directly in the repo's git history.

## 1. Set up the repo + GitHub Pages

1. In your website repo, create the file `data/listings.json` with just `[]`
   in it, and commit it. (The Worker needs it to already exist the first
   time — after that it manages the file itself.)
2. In the repo's Settings → Pages, enable GitHub Pages for the branch/folder
   your site lives in.
3. Once published, your data file is reachable at:
   ```
   https://YOUR-USERNAME.github.io/YOUR-REPO/data/listings.json
   ```
   That's the `DATA_URL` the website will fetch from.

## 2. Create a scoped GitHub token

Go to GitHub → Settings → Developer settings → **Fine-grained personal
access tokens** → Generate new token.

- **Repository access:** "Only select repositories" → pick just this one repo
- **Permissions:** Contents → **Read and write**. Nothing else.

This narrow scope matters — this token can only touch this one repo's
contents, so even in the worst case (if it ever leaked) the blast radius is
small. Copy the token now; you won't see it again.

## 3. Deploy the Worker

1. Install the CLI:
   ```
   npm install -g wrangler
   ```
2. Log in:
   ```
   wrangler login
   ```
3. Edit `wrangler.toml`: set `GITHUB_OWNER`, `GITHUB_REPO`, `GITHUB_BRANCH`,
   `GITHUB_PATH` to match your repo.
4. Set the two secrets:
   ```
   wrangler secret put API_KEY
   ```
   (make up any random string — this is what the mod authenticates with)
   ```
   wrangler secret put GITHUB_TOKEN
   ```
   (paste the fine-grained token from step 2 — this one is never exposed to
   players, it only lives in the Worker)
5. Deploy:
   ```
   wrangler deploy
   ```
   Prints your live URL, e.g. `https://snailcraft-trading-post.YOURNAME.workers.dev`

## 4. Wire everything up

- **Mod** (`ShopUploader.java`): set `API_BASE` to your Worker URL and
  `API_KEY` to the same string from step 4. That's the *only* secret the
  mod ever knows — it never touches your GitHub token.
- **Website**: set `DATA_URL` to the GitHub Pages URL from step 1, and (if
  you keep the manual-upload fallback) `API_BASE`/`API_KEY` to the same
  Worker values as the mod.

## What happens on each upload

1. Mod POSTs its current shop log to the Worker with `Authorization: Bearer <API_KEY>`.
2. Worker checks the key, then uses the GitHub token to fetch the current
   `data/listings.json`, merge in the new rows (same seller+item = same
   listing, newest wins, diamonds/diamond blocks dropped), and commit the
   result back.
3. GitHub Pages rebuilds automatically (usually under a minute).
4. Anyone loading the website fetches the fresh JSON directly — no Worker
   call needed for reads at all.

If nothing actually changed (no new/updated rows), the Worker skips the
commit entirely — so idle players polling on a timer won't spam your repo's
commit history.

## Recovering from data loss

If commits ever shrink unexpectedly (this happened once — see the
"Fixed: large-file read bug" note below), you don't need to restore from a
single old snapshot. Pull in **every** version of the file from the
affected time window and merge them all together, since different commits
during an intermittent bug can each hold pieces that don't exist in any
other single commit.

```bash
# From inside a local clone of your website repo:
./gather-snapshots.sh "6 hours ago"          # adjust the window as needed
node recover-all.js snapshots/*.json > merged-listings.json

mv merged-listings.json data/listings.json
git add data/listings.json
git commit -m "Recover listings from the last 6 hours of commits"
git push
```

`gather-snapshots.sh` pulls out every commit that touched
`data/listings.json` in the given window, plus one commit right before it
as a safety baseline. `recover-all.js` merges all of them (any number of
files, order doesn't matter), keeping the newest version of every
`world+seller+baseItem+itemName` listing wherever it happened to appear —
so nothing that ever existed in that window gets lost even if it was wiped
again before the most recent commit.

### Fixed: large-file read bug

Earlier versions of this Worker read the current file via GitHub's default
JSON response, which silently returns an **empty** `content` field for any
file over 1MB. Once `listings.json` grew past that size, the Worker would
think the file was empty and commit only the latest upload batch as the
entire file — wiping everything else. This is fixed now (the Worker
fetches raw content directly, which works up to 100MB), but any commits
made before the fix was deployed may have lost data — use the recovery
steps above to pull it back.

## Honest notes

- **Every real upload is a git commit.** If you have many active players
  auto-uploading frequently, your commit history will grow fast. Consider
  a longer auto-upload interval in the mod (e.g. every 15–30 minutes rather
  than every few minutes) if that bothers you.
- **The shared API_KEY is a deterrent, not real security** — it stops
  casual spam, but anyone who extracts it (from the mod jar, or from the
  website's page source if you keep manual upload) can post data. It
  *cannot* touch your repo directly though, since only the Worker holds
  the actual GitHub token.
- **Two near-simultaneous uploads** are handled with a small retry-on-409
  loop (GitHub rejects a commit built against a stale file version), but
  under very heavy concurrent load a few could still fail — the mod will
  just report the failure in chat and try again next cycle.
