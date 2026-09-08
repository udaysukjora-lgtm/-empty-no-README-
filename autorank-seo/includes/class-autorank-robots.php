<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Points WordPress's built-in virtual robots.txt at our sitemap, and blocks
 * indexing site-wide automatically when the admin has WP's own
 * "discourage search engines" setting on (staging sites shouldn't rank).
 */
class AutoRank_Robots {

	public function __construct() {
		add_filter( 'robots_txt', array( $this, 'add_sitemap_line' ), 10, 2 );
	}

	public function add_sitemap_line( string $output, $public ): string {
		$output .= "\nSitemap: " . home_url( '/sitemap.xml' ) . "\n";
		return $output;
	}
}
