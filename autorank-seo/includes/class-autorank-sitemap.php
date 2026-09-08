<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Serves an auto-generated XML sitemap at /sitemap.xml — no manual
 * regeneration needed, it's built fresh on each request from published
 * posts/pages/public custom post types.
 */
class AutoRank_Sitemap {

	public function __construct() {
		add_action( 'init', array( $this, 'add_rewrite_rule' ) );
		add_filter( 'query_vars', array( $this, 'add_query_var' ) );
		add_action( 'template_redirect', array( $this, 'maybe_render_sitemap' ) );
	}

	public function add_rewrite_rule(): void {
		add_rewrite_rule( '^sitemap\.xml$', 'index.php?autorank_sitemap=1', 'top' );
	}

	public function add_query_var( array $vars ): array {
		$vars[] = 'autorank_sitemap';
		return $vars;
	}

	public static function flush_rewrite_on_activate(): void {
		( new self() )->add_rewrite_rule();
		flush_rewrite_rules();
	}

	public static function flush_rewrite_on_deactivate(): void {
		flush_rewrite_rules();
	}

	public function maybe_render_sitemap(): void {
		if ( '1' !== get_query_var( 'autorank_sitemap' ) ) {
			return;
		}

		header( 'Content-Type: application/xml; charset=UTF-8' );
		echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
		echo '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' . "\n";

		echo $this->url_entry( home_url( '/' ), get_lastpostmodified( 'GMT' ), '1.0' );

		$post_types = get_post_types( array( 'public' => true ) );
		unset( $post_types['attachment'] );

		foreach ( $post_types as $post_type ) {
			$posts = get_posts(
				array(
					'post_type'      => $post_type,
					'post_status'    => 'publish',
					'posts_per_page' => 2000,
					'orderby'        => 'modified',
					'order'          => 'DESC',
				)
			);

			foreach ( $posts as $post ) {
				$priority = ( 'page' === $post_type && 'page' === get_option( 'show_on_front' ) && (int) get_option( 'page_on_front' ) === $post->ID ) ? '1.0' : '0.8';
				echo $this->url_entry( get_permalink( $post ), $post->post_modified_gmt, $priority );
			}
		}

		foreach ( get_terms( array( 'taxonomy' => get_taxonomies( array( 'public' => true ) ), 'hide_empty' => true ) ) as $term ) {
			echo $this->url_entry( get_term_link( $term ), '', '0.6' );
		}

		echo '</urlset>';
		exit;
	}

	private function url_entry( string $loc, string $lastmod = '', string $priority = '0.5' ): string {
		if ( is_wp_error( $loc ) ) {
			return '';
		}
		$xml = "\t<url>\n\t\t<loc>" . esc_url( $loc ) . "</loc>\n";
		if ( $lastmod ) {
			$xml .= "\t\t<lastmod>" . mysql2date( 'c', $lastmod, false ) . "</lastmod>\n";
		}
		$xml .= "\t\t<priority>{$priority}</priority>\n\t</url>\n";
		return $xml;
	}
}
