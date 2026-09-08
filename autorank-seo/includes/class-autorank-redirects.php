<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * 404 monitor + redirect manager. Every 404 hit is logged; the admin can
 * turn any logged 404 into a 301 redirect from the dashboard (see
 * class-autorank-admin.php), and those redirects are then applied
 * automatically on every request before WordPress renders the 404 page.
 */
class AutoRank_Redirects {

	const OPTION_REDIRECTS = 'autorank_redirects';
	const OPTION_404_LOG   = 'autorank_404_log';

	public function __construct() {
		add_action( 'template_redirect', array( $this, 'maybe_redirect' ), 0 );
		add_action( 'template_redirect', array( $this, 'log_404' ), 100 );
	}

	public function maybe_redirect(): void {
		$path = $this->current_path();
		$map  = get_option( self::OPTION_REDIRECTS, array() );

		if ( isset( $map[ $path ] ) ) {
			wp_safe_redirect( home_url( $map[ $path ] ), 301 );
			exit;
		}
	}

	public function log_404(): void {
		if ( ! is_404() ) {
			return;
		}

		$path = $this->current_path();
		$log  = get_option( self::OPTION_404_LOG, array() );

		$log[ $path ] = array(
			'hits'      => isset( $log[ $path ] ) ? $log[ $path ]['hits'] + 1 : 1,
			'last_seen' => current_time( 'mysql' ),
		);

		// Cap the log so it can't grow unbounded on a heavily-scanned site.
		if ( count( $log ) > 500 ) {
			uasort( $log, fn( $a, $b ) => $b['hits'] <=> $a['hits'] );
			$log = array_slice( $log, 0, 500, true );
		}

		update_option( self::OPTION_404_LOG, $log, false );
	}

	private function current_path(): string {
		$path = wp_parse_url( add_query_arg( array() ), PHP_URL_PATH );
		return '/' . ltrim( (string) $path, '/' );
	}

	public static function add_redirect( string $from, string $to ): void {
		$map          = get_option( self::OPTION_REDIRECTS, array() );
		$map[ $from ] = $to;
		update_option( self::OPTION_REDIRECTS, $map, false );

		$log = get_option( self::OPTION_404_LOG, array() );
		unset( $log[ $from ] );
		update_option( self::OPTION_404_LOG, $log, false );
	}

	public static function delete_redirect( string $from ): void {
		$map = get_option( self::OPTION_REDIRECTS, array() );
		unset( $map[ $from ] );
		update_option( self::OPTION_REDIRECTS, $map, false );
	}
}
