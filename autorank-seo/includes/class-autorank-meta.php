<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Auto-generates SEO title and meta description for every public post/page
 * that doesn't already have one set (via the post meta box in class-autorank-admin.php),
 * and prints them plus Open Graph / Twitter Card tags in <head>.
 */
class AutoRank_Meta {

	public function __construct() {
		add_action( 'wp_head', array( $this, 'output_meta_tags' ), 1 );
		add_filter( 'pre_get_document_title', array( $this, 'filter_document_title' ) );
	}

	/**
	 * Makes the actual <title> tag match what we compute for og:title —
	 * without this, a custom SEO title only reached social share previews,
	 * not the title search engines show in results.
	 */
	public function filter_document_title( string $title ): string {
		if ( is_admin() ) {
			return $title;
		}
		return $this->get_title();
	}

	public function output_meta_tags(): void {
		if ( is_admin() ) {
			return;
		}

		$title       = $this->get_title();
		$description = $this->get_description();
		$canonical   = $this->get_canonical();

		echo "\n<!-- AutoRank SEO -->\n";
		echo '<meta name="description" content="' . esc_attr( $description ) . '" />' . "\n";
		echo '<link rel="canonical" href="' . esc_url( $canonical ) . '" />' . "\n";

		echo '<meta property="og:title" content="' . esc_attr( $title ) . '" />' . "\n";
		echo '<meta property="og:description" content="' . esc_attr( $description ) . '" />' . "\n";
		echo '<meta property="og:url" content="' . esc_url( $canonical ) . '" />' . "\n";
		echo '<meta property="og:type" content="' . ( is_singular() ? 'article' : 'website' ) . '" />' . "\n";

		$image = $this->get_social_image();
		if ( $image ) {
			echo '<meta property="og:image" content="' . esc_url( $image ) . '" />' . "\n";
			echo '<meta name="twitter:card" content="summary_large_image" />' . "\n";
			echo '<meta name="twitter:image" content="' . esc_url( $image ) . '" />' . "\n";
		} else {
			echo '<meta name="twitter:card" content="summary" />' . "\n";
		}

		echo '<meta name="twitter:title" content="' . esc_attr( $title ) . '" />' . "\n";
		echo '<meta name="twitter:description" content="' . esc_attr( $description ) . '" />' . "\n";
		echo "<!-- /AutoRank SEO -->\n";
	}

	private function get_title(): string {
		if ( is_singular() ) {
			$custom = get_post_meta( get_queried_object_id(), '_autorank_title', true );
			if ( $custom ) {
				return $custom;
			}
			return get_the_title() . ' | ' . get_bloginfo( 'name' );
		}

		if ( is_category() || is_tag() || is_tax() ) {
			return single_term_title( '', false ) . ' | ' . get_bloginfo( 'name' );
		}

		if ( is_search() ) {
			return sprintf( 'Search results for "%s" | %s', get_search_query(), get_bloginfo( 'name' ) );
		}

		return get_bloginfo( 'name' ) . ' | ' . get_bloginfo( 'description' );
	}

	private function get_description(): string {
		if ( is_singular() ) {
			$custom = get_post_meta( get_queried_object_id(), '_autorank_description', true );
			if ( $custom ) {
				return $custom;
			}
			return $this->auto_excerpt( get_queried_object_id() );
		}

		if ( is_category() || is_tag() || is_tax() ) {
			$desc = term_description();
			return $desc ? wp_strip_all_tags( $desc ) : get_bloginfo( 'description' );
		}

		return get_bloginfo( 'description' );
	}

	/**
	 * Builds a ~155 char description from the post excerpt/content when the
	 * author hasn't written one — this is the "automatic" part.
	 */
	public function auto_excerpt( int $post_id ): string {
		$post = get_post( $post_id );
		if ( ! $post ) {
			return '';
		}

		$source = $post->post_excerpt ? $post->post_excerpt : $post->post_content;
		$text   = wp_strip_all_tags( strip_shortcodes( $source ) );
		$text   = preg_replace( '/\s+/', ' ', trim( $text ) );

		if ( strlen( $text ) <= 155 ) {
			return $text;
		}

		$truncated = substr( $text, 0, 155 );
		$truncated = substr( $truncated, 0, strrpos( $truncated, ' ' ) );
		return $truncated . '…';
	}

	private function get_canonical(): string {
		if ( is_singular() ) {
			return get_permalink();
		}
		global $wp;
		return home_url( add_query_arg( array(), $wp->request ) );
	}

	private function get_social_image() {
		if ( is_singular() && has_post_thumbnail() ) {
			$src = wp_get_attachment_image_src( get_post_thumbnail_id(), 'large' );
			return $src ? $src[0] : false;
		}
		return false;
	}
}
