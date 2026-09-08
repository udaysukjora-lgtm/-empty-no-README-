<?php
if ( ! defined( 'WP_UNINSTALL_PLUGIN' ) ) {
	exit;
}

delete_option( 'autorank_redirects' );
delete_option( 'autorank_404_log' );

$post_ids = get_posts(
	array(
		'post_type'      => 'any',
		'posts_per_page' => -1,
		'fields'         => 'ids',
		'meta_key'       => '_autorank_seo_score',
	)
);

foreach ( $post_ids as $post_id ) {
	delete_post_meta( $post_id, '_autorank_seo_score' );
	delete_post_meta( $post_id, '_autorank_seo_issues' );
	delete_post_meta( $post_id, '_autorank_title' );
	delete_post_meta( $post_id, '_autorank_description' );
	delete_post_meta( $post_id, '_autorank_focus_keyword' );
}
