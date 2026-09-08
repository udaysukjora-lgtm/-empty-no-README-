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

		$readability = $this->analyze_readability( wp_strip_all_tags( $post->post_content ) );
		update_post_meta( $post_id, '_autorank_readability_score', $readability['score'] );
		update_post_meta( $post_id, '_autorank_readability_label', $readability['label'] );
		update_post_meta( $post_id, '_autorank_readability_issues', $readability['issues'] );
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

	/**
	 * Flesch Reading Ease (0-100, higher = easier to read), plus plain-English
	 * feedback on the two things that move it most: sentence length and
	 * word complexity.
	 */
	public function analyze_readability( string $text ): array {
		$text = trim( preg_replace( '/\s+/', ' ', $text ) );

		if ( '' === $text ) {
			return array(
				'score'  => 0,
				'label'  => 'No content',
				'issues' => array( 'Add some content to get a readability score.' ),
			);
		}

		$sentences = preg_split( '/[.!?]+(?:\s|$)/', $text, -1, PREG_SPLIT_NO_EMPTY );
		$sentence_count = max( 1, count( $sentences ) );

		preg_match_all( '/[A-Za-z\'-]+/', $text, $word_matches );
		$words = $word_matches[0];
		$word_count = max( 1, count( $words ) );

		$syllable_count = 0;
		foreach ( $words as $word ) {
			$syllable_count += $this->count_syllables( $word );
		}

		$avg_words_per_sentence   = $word_count / $sentence_count;
		$avg_syllables_per_word   = $syllable_count / $word_count;

		$flesch = 206.835 - ( 1.015 * $avg_words_per_sentence ) - ( 84.6 * $avg_syllables_per_word );
		$flesch = max( 0, min( 100, round( $flesch ) ) );

		if ( $flesch >= 80 ) {
			$label = 'Very easy';
		} elseif ( $flesch >= 60 ) {
			$label = 'Easy';
		} elseif ( $flesch >= 50 ) {
			$label = 'Fairly difficult';
		} elseif ( $flesch >= 30 ) {
			$label = 'Difficult';
		} else {
			$label = 'Very difficult';
		}

		$issues = array();
		if ( $avg_words_per_sentence > 20 ) {
			$issues[] = 'Sentences average ' . round( $avg_words_per_sentence ) . ' words — split some up (aim for under 20).';
		}
		if ( $avg_syllables_per_word > 1.6 ) {
			$issues[] = 'Word choice leans complex — swap in shorter, simpler words where you can.';
		}

		$long_sentences = 0;
		foreach ( $sentences as $sentence ) {
			if ( str_word_count( $sentence ) > 30 ) {
				$long_sentences++;
			}
		}
		if ( $long_sentences > 0 ) {
			$issues[] = $long_sentences . ' sentence(s) are over 30 words long.';
		}

		if ( empty( $issues ) ) {
			$issues[] = 'Readability looks good.';
		}

		return array(
			'score'  => (int) $flesch,
			'label'  => $label,
			'issues' => $issues,
		);
	}

	/**
	 * Rough English syllable count: counts vowel-sound groups, then trims
	 * the usual silent trailing "e". Good enough for a writing-aid heuristic
	 * — not a dictionary lookup.
	 */
	private function count_syllables( string $word ): int {
		$word = strtolower( preg_replace( '/[^a-z]/i', '', $word ) );
		if ( '' === $word ) {
			return 0;
		}

		preg_match_all( '/[aeiouy]+/', $word, $groups );
		$count = count( $groups[0] );

		if ( strlen( $word ) > 2 && substr( $word, -1 ) === 'e' && substr( $word, -2 ) !== 'le' ) {
			$count--;
		}

		return max( 1, $count );
	}
}
