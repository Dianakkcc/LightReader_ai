package com.lightreader.core.format

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Chapters come from arbitrary files, so scripts, frames and event handlers are stripped before
 * the HTML ever reaches the WebView. Styling and images are preserved.
 */
object HtmlSanitizer {

    private val dangerousTags = setOf(
        "script", "iframe", "frame", "frameset", "object", "embed", "applet",
        "form", "input", "button", "select", "textarea", "noscript", "link", "meta", "base",
    )

    private val allowedTags = setOf(
        "p", "div", "span", "br", "hr", "wbr", "b", "strong", "i", "em", "u", "s", "strike",
        "del", "ins", "mark", "sub", "sup", "small", "big", "code", "pre", "kbd", "samp", "var",
        "blockquote", "q", "cite", "abbr", "dfn", "time", "address",
        "h1", "h2", "h3", "h4", "h5", "h6", "hgroup",
        "ul", "ol", "li", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption", "colgroup", "col",
        "img", "figure", "figcaption", "picture", "source", "svg", "g", "path", "rect", "circle",
        "polygon", "polyline", "line", "ellipse", "text", "tspan", "defs", "use", "symbol",
        "a", "ruby", "rt", "rp", "bdi", "bdo", "center", "font",
    )

    fun parse(fragmentOrDocument: String): Document = Jsoup.parse(fragmentOrDocument)

    /** Removes everything the reader must not execute, returns cleaned body HTML. */
    fun cleanToBodyHtml(html: String): String {
        val document = Jsoup.parse(html)
        sanitize(document)
        return document.body().html()
    }

    fun sanitize(document: Document) {
        document.select(dangerousTags.joinToString(",")).forEach { it.remove() }
        document.getAllElements().forEach { element ->
            if (element === document) return@forEach
            if (element.tagName().lowercase() !in allowedTags && element.tagName().lowercase() !in
                setOf("html", "head", "body", "style")
            ) {
                element.unwrap()
                return@forEach
            }
            sanitizeAttributes(element)
        }
    }

    private fun sanitizeAttributes(element: Element) {
        val names = element.attributes().map { it.key }
        for (name in names) {
            val lower = name.lowercase()
            when {
                lower.startsWith("on") -> element.removeAttr(name)
                lower == "srcdoc" -> element.removeAttr(name)
                lower == "href" || lower == "src" || lower == "xlink:href" -> {
                    val value = element.attr(name).trim()
                    if (value.startsWith("javascript:", ignoreCase = true) ||
                        value.startsWith("data:text/html", ignoreCase = true) ||
                        value.startsWith("vbscript:", ignoreCase = true)
                    ) {
                        element.removeAttr(name)
                    }
                }

                lower == "style" -> {
                    val value = element.attr(name)
                    if (value.contains("expression(") || value.contains("javascript:", true)) {
                        element.removeAttr(name)
                    }
                }
            }
        }
        if (element.tagName() == "a" && element.hasAttr("href")) {
            // Keep the reader inside the document: external links are opened by the host app.
            element.attr("data-href", element.attr("href"))
        }
    }
}
