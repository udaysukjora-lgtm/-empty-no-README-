<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Scores a post's on-page SEO (0-100) from title/description length,
 * content length, focus-keyword usage, image alt coverage and internal
 * links — recalculated on every save and shown in the editor sidebar and
 * the site-wide dashboard.
 */
class AutoRank_Analyzer {

	public function __construct() {
		add_action( 'save_post', array( $this, 'score_on_save' ), 20, 2 );
	}

	public function score_on_save( int $post_id, WP_Post $post ): void {
		if ( wp_is_post_autosave( $post_id ) || wp_is_post_revision( $post_id ) ) {
			return;
		}
		if ( ! in_array( $post->post_status, array( 'publish', 'draft', 'pending', 'future' ), true ) ) {
			return;
		}

		$result = $this->analyze( $post_id );
		update_post_meta( $post_id, '_autorank_seo_score', $result['score'] );
		update_post_meta( $post_id, '_autorank_seo_issues', $result['issues'] );
	}

	public function analyze( int $post_id ): array {
		$post    = get_post( $post_id );
		$content = wp_strip_all_tags( $post->post_content );
		$word_count = str_word_count( $content );

		$keyword = get_post_meta( $post_id, '_autorank_focus_keyword', true );
		$title   = get_post_meta( $post_id, '_autorank_title', true ) ?: $post->post_title;
		$desc    = get_post_meta( $post_id, '_autorank_description', true ) ?: ( new AutoRank_Meta() )->auto_excerpt( $post_id );

		$score  = 0;
		$issues = array();

		// Content length (25 pts).
		if ( $word_count >= 300 ) {
			$score += 25;
		} elseif ( $word_count >= 150 ) {
			$score += 12;
			$issues[] = 'Content is a bit short — aim for 300+ words.';
		} else {
			$issues[] = 'Content is too short (under 150 words).';
		}

		// Title length (15 pts).
		$title_len = strlen( $title );
		if ( $title_len >= 30 && $title_len <= 60 ) {
			$score += 15;
		} else {
			$issues[] = 'SEO title should be 30-60 characters (currently ' . $title_len . ').';
		}

		// Description length (15 pts).
		$desc_len = strlen( $desc );
		if ( $desc_len >= 70 && $desc_len <= 160 ) {
			$score += 15;
		} else {
			$issues[] = 'Meta description should be 70-160 characters (currently ' . $desc_len . ').';
		}

		// Focus keyword usage (25 pts).
		if ( $keyword ) {
			$kw = mb_strtolower( $keyword );
			$in_title   = false !== mb_stripos( $title, $kw );
			$in_desc    = false !== mb_stripos( $desc, $kw );
			$in_content = false !== mb_stripos( $content, $kw );

			$score += $in_title ? 10 : 0;
			$score += $in_desc ? 5 : 0;
			$score += $in_content ? 10 : 0;

			if ( ! $in_title ) {
				$issues[] = 'Focus keyword is missing from the SEO title.';
			}
			if ( ! $in_content ) {
				$issues[] = 'Focus keyword does not appear in the content.';
			}
		} else {
			$issues[] = 'No focus keyword set for this post.';
		}

		// Images have alt text (10 pts).
		preg_match_all( '/<img[^>]*>/i', $post->post_content, $imgs );
		if ( empty( $imgs[0] ) ) {
			$score += 10;
		} else {
			$missing_alt = 0;
			foreach ( $imgs[0] as $img ) {
				if ( ! preg_match( '/alt=("|\')[^"\']+\1/i', $img ) ) {
					$missing_alt++;
				}
			}
			if ( 0 === $missing_alt ) {
				$score += 10;
			} else {
				$issues[] = $missing_alt . ' image(s) missing alt text (AutoRank auto-fills this on the front end).';
			}
		}

		// Internal links (10 pts).
		$home_host = wp_parse_url( home_url(), PHP_URL_HOST );
		$has_internal_link = (bool) preg_match( '/<a[^>]+href=["\']https?:\/\/' . preg_quote( $home_host, '/' ) . '/i', $post->post_content )
			|| (bool) preg_match( '/<a[^>]+href=["\']\//i', $post->post_content );
		if ( $has_internal_link ) {
			$score += 10;
		} else {
			$issues[] = 'No internal links found — link to other relevant pages on your site.';
		}

		return array(
			'score'  => min( 100, $score ),
			'issues' => $issues,
		);
	}
}
