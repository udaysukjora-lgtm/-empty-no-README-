package com.example.askqustion.util

import android.text.Html

/** WordPress REST fields (title/content/excerpt.rendered) come back as HTML. */
fun String.htmlToPlainText(): String =
    Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
