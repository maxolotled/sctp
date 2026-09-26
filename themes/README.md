# Site themes

Every page links `/theme.css` (after its own styles) and `/theme.js`. Whatever
is in those two files restyles and decorates the whole site. The files in this
folder are the saved themes, one `.css` + `.js` pair each:

| Theme | Look |
| --- | --- |
| `original` | The normal green SCTP theme (the colours each page already has built in), no decorations. |
| `halloween` | Pumpkin orange on a dark plum night, spooky titles, moon and cobweb, jack-o-lanterns, falling leaves, a bat now and then, a witch hat on the snail. |
| `christmas` | Midnight blue with Christmas red, festive titles, a star and holly, fairy lights across the top, snowfall, Santa's sleigh, a snowman and presents, a Santa hat. |
| `newyear` | Black and champagne gold, art-deco titles, a mirror ball and a clock at midnight, fairy lights, confetti, fireworks, shooting stars, champagne, a top hat. The footer wishes a happy new year for the coming year automatically. |
| `easter` | Soft spring twilight in blossom pink and mint, a smiling sun and a cherry-blossom branch, pastel bunting, a hopping bunny, butterflies, painted eggs and a chick, bunny ears. |
| `birthday` | Snailcraft's birthday: party purple and hot pink, balloons and streamers, HAPPY BIRTHDAY SNAILCRAFT bunting, confetti pops, a cake with candles and presents, a party hat. |
| `summer` | A tropical beach: lagoon turquoise and sunset coral, script titles, a sunset over the sea behind a palm tree, palm fronds, a hibiscus lei across the top, seagulls and a scuttling crab, a beach umbrella, sandcastle, beach ball and coconut drink, sunglasses on the snail. |
| `valentines` | Candlelit deep rose and love-heart red, romantic script titles, a glowing heart and a rose, a string of hearts, hearts floating up, rose petals, winged hearts, a love letter and chocolates, heart boppers. |
| `autumn` | Cosy November (the spooky-cute follow-up to Halloween): cocoa browns and burnt orange, hand-written titles, a friendly ghost with cocoa, an acorn branch, a leaf garland, falling leaves and soft rain, cute mushrooms and pumpkin, a knitted beanie. |
| `pride` | Pride month: rainbow bunting, titles filled with a moving rainbow, a rainbow and the Progress flag, rainbow bursts, a Pride Duck waddling past, rubber ducks, a rainbow over the snail. |
| `aprilfools` | Actual pranks, nothing broken: the whole site in a comic font, a fake "SCTP NEWS" ticker, a one-time "SCTP Premium™" popup that reveals the joke, silly nav names, a "Download more RAM" button, a "come back!" tab title, cards that randomly shake. Plus the snail upside down with googly eyes, confetti pops, a rubber chicken and the snail speedrunning past. |
| `lunarnewyear` | Lucky red and gold, brush-style titles, a full moon with plum blossoms, firecrackers, red lanterns, firecracker bursts, a dragon flying past, red envelopes and gold ingots. The footer names the coming year's zodiac animal automatically. |

Every seasonal theme also swaps the page icons, the favicon and the tab title
emoji. Falling effects (leaves, snow, confetti, hearts) are only in Halloween,
autumn, Christmas, New Year's and Valentine's. Any theme with falling effects
or fireworks has a button in the bottom-left corner to turn them off
(remembered per visitor). For people whose system asks for reduced motion,
the effects start off.

## Previewing a theme

Add `?theme=<name>` to any page, e.g. `sctp.nl/?theme=christmas`. That tab
keeps showing that theme while you browse, with a "Previewing" banner at the
bottom. `?theme=live` (or the banner's Stop button) goes back to the live
theme. Nobody else sees a preview.

## Switching the live theme

Copy both files of the theme you want over `/theme.css` and `/theme.js`, then
commit and push:

```sh
cp themes/original.css theme.css && cp themes/original.js theme.js     # back to normal
cp themes/christmas.css theme.css && cp themes/christmas.js theme.js   # e.g. Christmas
```

GitHub Pages can take a few minutes to pick up the change.

## How it's built

- `<name>.css` sets the colour tokens, the title font, the body glow, the two
  corner pictures (`body::before` / `body::after`) and a few restyled bits.
- `<name>.js` is a config (artwork + which effects) plus a small preview
  handler. The effects themselves (particles, fireworks, flyers, the garland
  across the top, header props, footer banner, logo accessory, icons, the on/off
  button) all live in the shared `fx.js`. Its header comment lists every option.

## Making a new theme

Copy the pair closest to what you want, rename them, change `ID` in the `.js`
to the new name, then change the colours, artwork and effects. Leave `--warn`,
`--bad` and `--info` alone: they're status colours, and Rare-dle uses its own
versions of them.
