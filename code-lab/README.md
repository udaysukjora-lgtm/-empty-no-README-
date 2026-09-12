# Code Lab

Three tools on one site, for students practising code. Everything runs in the
browser — there is no server behind any of it, and nothing a student writes
leaves their own device.

| Page | What it is |
|---|---|
| `index.html` | Python notebook. Cells run one at a time and share one namespace, the way Colab does. |
| `web.html` | HTML, CSS and JS in three boxes, with a live preview and a console. Downloads as a three-file project. |
| `zip.html` | Seven ZIP jobs: create, extract, compress, merge, split, password, inspect. |

## Putting it online

Static files. Copy this folder to any web host — `public_html` on shared
hosting, a Netlify drop, GitHub Pages. No build step, no Node, no database.

One server setting matters: `.js` must be served as `text/javascript`. Apache
gets this from the `.htaccess` in this folder; most hosts do it already.

## Engines carried inside

- Brython 3.14 (BSD) — Python in the browser. `vendor/brython.js` plus
  `vendor/brython_stdlib.js`. Carried here rather than fetched from a CDN, so
  the pages keep working with no connection.
- zip.js 2.14.0 (BSD-3-Clause, © Gildas Lormeau) — `vendor/zip.min.js`.

## Editing it

The pages are built from `src/` by `build.py` in the working copy, not edited
in place. Shared look lives in `assets/shell.css`; each page adds its own.
