# Site themes

Every page links `/theme.css` (after its own styles) and `/theme.js`, so whatever
is in those two files restyles and decorates the whole site. The files in this
folder are the saved themes, one `.css` + `.js` pair each:

| File | Look |
| --- | --- |
| `original.css` + `original.js` | The normal green SCTP theme (the same colours each page already has built in, no decorations). |
| `halloween.css` + `halloween.js` | Fall / Halloween: pumpkin orange on a dark plum night, spooky titles, moon, bats, cobweb, jack-o-lanterns, falling leaves (with an on/off button), Halloween page icons and favicon. |

## Switching

Copy both files of the theme you want over `/theme.css` and `/theme.js`, then commit and push:

```sh
cp themes/original.css theme.css && cp themes/original.js theme.js     # back to normal
cp themes/halloween.css theme.css && cp themes/halloween.js theme.js   # Halloween
```

Empty `theme.css` / `theme.js` files also give the original look. GitHub Pages can take a few
minutes to pick up the change.

## Making a new theme

Copy `original.css`/`original.js`, change the colour tokens, and add decorations if you like.
Leave `--warn`, `--bad` and `--info` alone: they're status colours, and
Rare-dle uses its own versions of them.
