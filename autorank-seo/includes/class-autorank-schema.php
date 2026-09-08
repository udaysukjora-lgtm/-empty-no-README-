<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Prints JSON-LD structured data automatically — Organization/WebSite on
 * every page, Article on single posts, BreadcrumbList wherever applicable.
 */
class AutoRank_Schema {

	public function __construct() {
		add_action( 'wp_head', array( $this, 'output_schema' ), 5 );
	}

	public function output_schema(): void {
		if ( is_admin() ) {
			return;
		}

		$graphs = array( $this->website_graph() );

		if ( is_singular( 'post' ) ) {
			$graphs[] = $this->article_graph();
		}

		if ( ! is_front_page() ) {
			$graphs[] = $this->breadcrumb_graph();
		}

		$graphs = array_filter( $graphs );

		echo '<script type="application/ld+json">' . wp_json_encode(
			array(
				'@context' => 'https://schema.org',
				'@graph'   => array_values( $graphs ),
			)
		) . '</script>' . "\n";
	}

	private function website_graph(): array {
		return array(
			'@type' => 'WebSite',
			'@id'   => home_url( '/#website' ),
			'url'   => home_url( '/' ),
			'name'  => get_bloginfo( 'name' ),
		);
	}

	private function article_graph(): ?array {
		$post = get_queried_object();
		if ( ! $post instanceof WP_Post ) {
			return null;
		}

		$image = has_post_thumbnail( $post ) ? wp_get_attachment_image_url( get_post_thumbnail_id( $post ), 'large' ) : null;

		$data = array(
			'@type'         => 'Article',
			'@id'           => get_permalink( $post ) . '#article',
			'headline'      => get_the_title( $post ),
			'datePublished' => get_the_date( 'c', $post ),
			'dateModified'  => get_the_modified_date( 'c', $post ),
			'author'        => array(
				'@type' => 'Person',
				'name'  => get_the_author_meta( 'display_name', $post->post_author ),
			),
			'publisher'     => array(
				'@type' => 'Organization',
				'name'  => get_bloginfo( 'name' ),
			),
			'mainEntityOfPage' => get_permalink( $post ),
		);

		if ( $image ) {
			$data['image'] = $image;
		}

		return $data;
	}

	private function breadcrumb_graph(): array {
		$items = array(
			array(
				'@type'    => 'ListItem',
				'position' => 1,
				'name'     => get_bloginfo( 'name' ),
				'item'     => home_url( '/' ),
			),
		);

		if ( is_singular() ) {
			$items[] = array(
				'@type'    => 'ListItem',
				'position' => 2,
				'name'     => get_the_title(),
				'item'     => get_permalink(),
			);
		} elseif ( is_category() || is_tag() || is_tax() ) {
			$items[] = array(
				'@type'    => 'ListItem',
				'position' => 2,
				'name'     => single_term_title( '', false ),
				'item'     => get_term_link( get_queried_object() ),
			);
		}

		return array(
			'@type'           => 'BreadcrumbList',
			'itemListElement' => $items,
		);
	}
}
