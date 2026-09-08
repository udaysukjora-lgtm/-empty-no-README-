=== AutoRank SEO ===
Contributors: autorank
Tags: seo, sitemap, schema, redirects, meta tags
Requires at least: 5.8
Tested up to: 6.6
Requires PHP: 7.4
Stable tag: 1.0.0
License: GPLv2 or later
License URI: https://www.gnu.org/licenses/gpl-2.0.html

Automatic on-page SEO for WordPress: meta tags, XML sitemap, JSON-LD schema, image alt text, and a 404/redirect manager — all applied with zero setup, with per-post overrides when you want them.

== Description ==

AutoRank SEO fixes the on-page SEO basics automatically as soon as it's activated, and gives you a dashboard to see what still needs attention:

* **Meta tags** — SEO title, meta description, canonical URL, Open Graph and Twitter Card tags on every page. Auto-generated from your content when you don't set them yourself.
* **XML sitemap** — served live at `/sitemap.xml`, always up to date, linked from `robots.txt`.
* **Structured data** — JSON-LD for WebSite, Article and Breadcrumbs, so search engines understand your content.
* **Image alt text** — missing `alt` attributes are filled in automatically from the post title / attachment title.
* **404 monitor & redirects** — every broken link hit is logged; turn any of them into a 301 redirect with one click.
* **SEO score** — every post gets a 0-100 score with a list of concrete issues, recalculated automatically on save.

== Installation ==

1. Upload the `autorank-seo` folder to `/wp-content/plugins/`.
2. Activate the plugin through the "Plugins" menu in WordPress.
3. Open **AutoRank SEO** in the admin sidebar to see your site's score.

== Frequently Asked Questions ==

= Do I need to configure anything? =

No — sitemap, schema, meta tags and alt text all work immediately on activation. Per-post SEO title/description/focus keyword are optional overrides shown in the post editor.

= Does this guarantee higher rankings? =

No plugin can guarantee rankings — search engines weigh hundreds of signals, including ones outside on-page SEO (backlinks, site speed, content quality, competition). AutoRank SEO automates the on-page fundamentals that are within your control.

== Changelog ==

= 1.0.0 =
* Initial release.
