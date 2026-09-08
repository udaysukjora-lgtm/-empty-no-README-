<?php
/**
 * Plugin Name: AutoRank SEO
 * Plugin URI:  https://example.com/autorank-seo
 * Description: Automatic on-page SEO fixer for WordPress — meta tags, XML sitemap, JSON-LD schema, image alt text, 404/redirect manager and a per-post SEO score, all applied automatically with zero manual setup.
 * Version:     1.0.0
 * Author:      AutoRank
 * License:     GPL-2.0-or-later
 * Text Domain: autorank-seo
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit; // No direct access.
}

define( 'AUTORANK_SEO_VERSION', '1.0.0' );
define( 'AUTORANK_SEO_PATH', plugin_dir_path( __FILE__ ) );
define( 'AUTORANK_SEO_URL', plugin_dir_url( __FILE__ ) );

require_once AUTORANK_SEO_PATH . 'includes/class-autorank-meta.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-sitemap.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-schema.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-analyzer.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-alt-text.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-redirects.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-robots.php';
require_once AUTORANK_SEO_PATH . 'includes/class-autorank-admin.php';

/**
 * Boots every module. Each class wires its own hooks in its constructor,
 * so the modules stay independent of each other.
 */
final class AutoRank_SEO {

	private static ?AutoRank_SEO $instance = null;

	public static function instance(): AutoRank_SEO {
		if ( null === self::$instance ) {
			self::$instance = new self();
		}
		return self::$instance;
	}

	private function __construct() {
		new AutoRank_Meta();
		new AutoRank_Sitemap();
		new AutoRank_Schema();
		new AutoRank_Analyzer();
		new AutoRank_Alt_Text();
		new AutoRank_Redirects();
		new AutoRank_Robots();
		new AutoRank_Admin();
	}
}

register_activation_hook( __FILE__, array( 'AutoRank_Sitemap', 'flush_rewrite_on_activate' ) );
register_deactivation_hook( __FILE__, array( 'AutoRank_Sitemap', 'flush_rewrite_on_deactivate' ) );

add_action( 'plugins_loaded', array( 'AutoRank_SEO', 'instance' ) );
