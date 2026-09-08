<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * WP-admin UI: the per-post SEO meta box (title/description/focus keyword
 * + live score & issues) and the site-wide AutoRank SEO dashboard page
 * (average score, worst-performing posts, 404 log with one-click redirects).
 */
class AutoRank_Admin {

	public function __construct() {
		add_action( 'add_meta_boxes', array( $this, 'register_meta_box' ) );
		add_action( 'save_post', array( $this, 'save_meta_box' ) );
		add_action( 'admin_menu', array( $this, 'register_dashboard' ) );
		add_action( 'admin_post_autorank_add_redirect', array( $this, 'handle_add_redirect' ) );
		add_action( 'admin_post_autorank_delete_redirect', array( $this, 'handle_delete_redirect' ) );
	}

	/* ---------------- Per-post meta box ---------------- */

	public function register_meta_box(): void {
		foreach ( get_post_types( array( 'public' => true ) ) as $post_type ) {
			add_meta_box(
				'autorank_seo_box',
				'AutoRank SEO',
				array( $this, 'render_meta_box' ),
				$post_type,
				'normal',
				'high'
			);
		}
	}

	public function render_meta_box( WP_Post $post ): void {
		wp_nonce_field( 'autorank_save_meta', 'autorank_nonce' );

		$title       = get_post_meta( $post->ID, '_autorank_title', true );
		$description = get_post_meta( $post->ID, '_autorank_description', true );
		$keyword     = get_post_meta( $post->ID, '_autorank_focus_keyword', true );
		$score       = (int) get_post_meta( $post->ID, '_autorank_seo_score', true );
		$issues      = get_post_meta( $post->ID, '_autorank_seo_issues', true );

		$readability_score  = get_post_meta( $post->ID, '_autorank_readability_score', true );
		$readability_label  = get_post_meta( $post->ID, '_autorank_readability_label', true );
		$readability_issues = get_post_meta( $post->ID, '_autorank_readability_issues', true );

		$color              = $score >= 80 ? '#1a7e3c' : ( $score >= 50 ? '#b8860b' : '#c0392b' );
		$readability_color  = $readability_score >= 60 ? '#1a7e3c' : ( $readability_score >= 30 ? '#b8860b' : '#c0392b' );
		?>
		<p>
			<strong>SEO score: <span style="color: <?php echo esc_attr( $color ); ?>;"><?php echo esc_html( $score ?: '—' ); ?>/100</span></strong>
			&nbsp;&nbsp;
			<strong>Readability: <span style="color: <?php echo esc_attr( $readability_color ); ?>;"><?php echo esc_html( '' !== $readability_score ? $readability_score . '/100 (' . $readability_label . ')' : '—' ); ?></span></strong>
			<?php if ( ! $post->post_content ) : ?>
				<em>(save/publish to calculate)</em>
			<?php endif; ?>
		</p>
		<p>
			<label for="autorank_focus_keyword"><strong>Focus keyword</strong></label><br />
			<input type="text" id="autorank_focus_keyword" name="autorank_focus_keyword" class="widefat" value="<?php echo esc_attr( $keyword ); ?>" />
		</p>
		<p>
			<label for="autorank_title"><strong>SEO title</strong> <span id="autorank_title_count"></span></label><br />
			<input type="text" id="autorank_title" name="autorank_title" class="widefat" value="<?php echo esc_attr( $title ); ?>" placeholder="<?php echo esc_attr( $post->post_title . ' | ' . get_bloginfo( 'name' ) ); ?>" />
			<span class="description">Leave blank to auto-generate from the post title.</span>
		</p>
		<p>
			<label for="autorank_description"><strong>Meta description</strong> <span id="autorank_desc_count"></span></label><br />
			<textarea id="autorank_description" name="autorank_description" class="widefat" rows="3" placeholder="Leave blank to auto-generate from the content."><?php echo esc_textarea( $description ); ?></textarea>
		</p>
		<?php if ( ! empty( $issues ) ) : ?>
			<p><strong>SEO issues found:</strong></p>
			<ul style="list-style: disc; margin-left: 20px;">
				<?php foreach ( $issues as $issue ) : ?>
					<li><?php echo esc_html( $issue ); ?></li>
				<?php endforeach; ?>
			</ul>
		<?php endif; ?>
		<?php if ( ! empty( $readability_issues ) ) : ?>
			<p><strong>Readability feedback:</strong></p>
			<ul style="list-style: disc; margin-left: 20px;">
				<?php foreach ( $readability_issues as $issue ) : ?>
					<li><?php echo esc_html( $issue ); ?></li>
				<?php endforeach; ?>
			</ul>
		<?php endif; ?>
		<script>
		(function(){
			function bind(id, out, max){
				var el = document.getElementById(id), o = document.getElementById(out);
				if (!el || !o) return;
				function upd(){ o.textContent = el.value.length + '/' + max; }
				el.addEventListener('input', upd); upd();
			}
			bind('autorank_title', 'autorank_title_count', 60);
			bind('autorank_description', 'autorank_desc_count', 160);
		})();
		</script>
		<?php
	}

	public function save_meta_box( int $post_id ): void {
		if ( ! isset( $_POST['autorank_nonce'] ) || ! wp_verify_nonce( $_POST['autorank_nonce'], 'autorank_save_meta' ) ) {
			return;
		}
		if ( defined( 'DOING_AUTOSAVE' ) && DOING_AUTOSAVE ) {
			return;
		}
		if ( ! current_user_can( 'edit_post', $post_id ) ) {
			return;
		}

		$fields = array(
			'autorank_focus_keyword' => '_autorank_focus_keyword',
			'autorank_title'         => '_autorank_title',
			'autorank_description'   => '_autorank_description',
		);

		foreach ( $fields as $field => $meta_key ) {
			if ( isset( $_POST[ $field ] ) ) {
				update_post_meta( $post_id, $meta_key, sanitize_text_field( wp_unslash( $_POST[ $field ] ) ) );
			}
		}
	}

	/* ---------------- Dashboard ---------------- */

	public function register_dashboard(): void {
		add_menu_page(
			'AutoRank SEO',
			'AutoRank SEO',
			'manage_options',
			'autorank-seo',
			array( $this, 'render_dashboard' ),
			'dashicons-chart-line',
			80
		);
	}

	public function render_dashboard(): void {
		if ( ! current_user_can( 'manage_options' ) ) {
			return;
		}

		global $wpdb;
		$posts = get_posts(
			array(
				'post_type'      => get_post_types( array( 'public' => true ) ),
				'post_status'    => 'publish',
				'posts_per_page' => -1,
			)
		);

		$scores             = array();
		$readability_scores = array();
		foreach ( $posts as $p ) {
			$s = get_post_meta( $p->ID, '_autorank_seo_score', true );
			if ( '' !== $s ) {
				$scores[ $p->ID ] = (int) $s;
			}
			$r = get_post_meta( $p->ID, '_autorank_readability_score', true );
			if ( '' !== $r ) {
				$readability_scores[ $p->ID ] = (int) $r;
			}
		}
		$avg             = $scores ? round( array_sum( $scores ) / count( $scores ) ) : 0;
		$avg_readability = $readability_scores ? round( array_sum( $readability_scores ) / count( $readability_scores ) ) : 0;
		asort( $scores );
		$worst = array_slice( $scores, 0, 10, true );

		$log       = get_option( AutoRank_Redirects::OPTION_404_LOG, array() );
		$redirects = get_option( AutoRank_Redirects::OPTION_REDIRECTS, array() );
		arsort( $log );
		?>
		<div class="wrap">
			<h1>AutoRank SEO Dashboard</h1>

			<div style="display:flex; gap:24px; margin: 20px 0;">
				<div style="background:#fff; border:1px solid #ccd0d4; padding:20px; min-width:160px;">
					<div style="font-size:32px; font-weight:bold;"><?php echo esc_html( $avg ); ?>/100</div>
					<div>Average SEO score (<?php echo count( $scores ); ?> posts scored)</div>
				</div>
				<div style="background:#fff; border:1px solid #ccd0d4; padding:20px; min-width:160px;">
					<div style="font-size:32px; font-weight:bold;"><?php echo esc_html( $avg_readability ); ?>/100</div>
					<div>Average readability (<?php echo count( $readability_scores ); ?> posts scored)</div>
				</div>
				<div style="background:#fff; border:1px solid #ccd0d4; padding:20px; min-width:160px;">
					<div style="font-size:32px; font-weight:bold;"><?php echo esc_html( count( $log ) ); ?></div>
					<div>Unique 404s logged</div>
				</div>
				<div style="background:#fff; border:1px solid #ccd0d4; padding:20px; min-width:160px;">
					<div style="font-size:32px; font-weight:bold;"><?php echo esc_html( count( $redirects ) ); ?></div>
					<div>Active redirects</div>
				</div>
			</div>

			<h2>Posts needing attention</h2>
			<?php if ( empty( $worst ) ) : ?>
				<p>Nothing scored yet — scores are calculated automatically when you save a post.</p>
			<?php else : ?>
				<table class="widefat striped">
					<thead><tr><th>Post</th><th>Score</th><th></th></tr></thead>
					<tbody>
					<?php foreach ( $worst as $post_id => $score ) : ?>
						<tr>
							<td><?php echo esc_html( get_the_title( $post_id ) ); ?></td>
							<td><?php echo esc_html( $score ); ?>/100</td>
							<td><a href="<?php echo esc_url( get_edit_post_link( $post_id ) ); ?>">Fix now</a></td>
						</tr>
					<?php endforeach; ?>
					</tbody>
				</table>
			<?php endif; ?>

			<h2 style="margin-top:32px;">404s &amp; redirects</h2>
			<?php if ( empty( $log ) && empty( $redirects ) ) : ?>
				<p>No 404s logged yet.</p>
			<?php else : ?>
				<table class="widefat striped">
					<thead><tr><th>URL</th><th>Hits</th><th>Redirect to</th><th></th></tr></thead>
					<tbody>
					<?php foreach ( $log as $path => $info ) : ?>
						<tr>
							<td><code><?php echo esc_html( $path ); ?></code></td>
							<td><?php echo esc_html( $info['hits'] ); ?></td>
							<td colspan="2">
								<form method="post" action="<?php echo esc_url( admin_url( 'admin-post.php' ) ); ?>" style="display:flex; gap:8px;">
									<?php wp_nonce_field( 'autorank_add_redirect' ); ?>
									<input type="hidden" name="action" value="autorank_add_redirect" />
									<input type="hidden" name="from" value="<?php echo esc_attr( $path ); ?>" />
									<input type="text" name="to" placeholder="/new-url/" class="regular-text" required />
									<button class="button">Redirect (301)</button>
								</form>
							</td>
						</tr>
					<?php endforeach; ?>
					<?php foreach ( $redirects as $from => $to ) : ?>
						<tr>
							<td><code><?php echo esc_html( $from ); ?></code></td>
							<td>—</td>
							<td>→ <code><?php echo esc_html( $to ); ?></code> <em>(active)</em></td>
							<td>
								<a href="<?php echo esc_url( wp_nonce_url( admin_url( 'admin-post.php?action=autorank_delete_redirect&from=' . rawurlencode( $from ) ), 'autorank_delete_redirect' ) ); ?>">Remove</a>
							</td>
						</tr>
					<?php endforeach; ?>
					</tbody>
				</table>
			<?php endif; ?>

			<h2 style="margin-top:32px;">What's automatic</h2>
			<ul style="list-style:disc; margin-left:20px;">
				<li>Meta title, description, canonical, Open Graph &amp; Twitter tags on every page</li>
				<li>XML sitemap at <code><?php echo esc_html( home_url( '/sitemap.xml' ) ); ?></code>, linked from robots.txt</li>
				<li>JSON-LD structured data (WebSite, Article, Breadcrumbs)</li>
				<li>Missing image alt text filled in automatically</li>
				<li>404s logged; turn any of them into a 301 redirect above</li>
				<li>Readability score (Flesch Reading Ease) with plain-English feedback on every post</li>
			</ul>
		</div>
		<?php
	}

	public function handle_add_redirect(): void {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( 'Unauthorized', 403 );
		}
		check_admin_referer( 'autorank_add_redirect' );

		$from = isset( $_POST['from'] ) ? sanitize_text_field( wp_unslash( $_POST['from'] ) ) : '';
		$to   = isset( $_POST['to'] ) ? sanitize_text_field( wp_unslash( $_POST['to'] ) ) : '';

		if ( $from && $to ) {
			AutoRank_Redirects::add_redirect( $from, $to );
		}

		wp_safe_redirect( admin_url( 'admin.php?page=autorank-seo' ) );
		exit;
	}

	public function handle_delete_redirect(): void {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( 'Unauthorized', 403 );
		}
		check_admin_referer( 'autorank_delete_redirect' );

		$from = isset( $_GET['from'] ) ? sanitize_text_field( wp_unslash( $_GET['from'] ) ) : '';
		if ( $from ) {
			AutoRank_Redirects::delete_redirect( $from );
		}

		wp_safe_redirect( admin_url( 'admin.php?page=autorank-seo' ) );
		exit;
	}
}
