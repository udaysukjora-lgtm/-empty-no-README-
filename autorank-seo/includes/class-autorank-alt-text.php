<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Auto-fills missing image alt text — one of the most common on-page SEO
 * gaps — using the attachment title/post title as a fallback, both on
 * upload and by rewriting <img> tags in rendered content that still lack
 * an alt attribute.
 */
class AutoRank_Alt_Text {

	public function __construct() {
		add_action( 'add_attachment', array( $this, 'set_alt_on_upload' ) );
		add_filter( 'the_content', array( $this, 'fill_missing_alt_in_content' ), 20 );
	}

	public function set_alt_on_upload( int $attachment_id ): void {
		if ( ! wp_attachment_is_image( $attachment_id ) ) {
			return;
		}

		$existing = get_post_meta( $attachment_id, '_wp_attachment_image_alt', true );
		if ( $existing ) {
			return;
		}

		$attachment = get_post( $attachment_id );
		$alt        = $attachment->post_title ? $attachment->post_title : get_bloginfo( 'name' );
		update_post_meta( $attachment_id, '_wp_attachment_image_alt', sanitize_text_field( $alt ) );
	}

	public function fill_missing_alt_in_content( string $content ): string {
		if ( ! is_singular() || false === strpos( $content, '<img' ) ) {
			return $content;
		}

		$fallback = get_the_title();

		return preg_replace_callback(
			'/<img\s+(?![^>]*\balt=)([^>]*)>/i',
			function ( $matches ) use ( $fallback ) {
				return '<img alt="' . esc_attr( $fallback ) . '" ' . $matches[1] . '>';
			},
			$content
		);
	}
}
