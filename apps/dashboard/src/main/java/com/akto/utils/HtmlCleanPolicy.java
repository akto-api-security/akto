package com.akto.utils;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class HtmlCleanPolicy {

    private static Predicate<String> matchesEither(
            final Pattern a, final Pattern b) {
        return s -> a.matcher(s).matches() || b.matcher(s).matches();
    }

    // Some common regular expression definitions.

    // The 16 colors defined by the HTML Spec (also used by the CSS Spec)
    private static final Pattern COLOR_NAME = Pattern.compile(
            "(?:aqua|black|blue|fuchsia|gray|grey|green|lime|maroon|navy|olive|purple"
                    + "|red|silver|teal|white|yellow)");

    // HTML/CSS Spec allows 3 or 6 digit hex to specify color
    private static final Pattern COLOR_CODE = Pattern.compile(
            "(?:#(?:[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?))");

    private static final Pattern NUMBER_OR_PERCENT = Pattern.compile(
            "[0-9]+%?");
    private static final Pattern PARAGRAPH = Pattern.compile(
            "(?:[\\p{L}\\p{N},'\\.\\s\\-_\\(\\)]|&[0-9]{2};)*");
    private static final Pattern HTML_ID = Pattern.compile(
            "[a-zA-Z0-9\\:\\-_\\.]+");
    // force non-empty with a '+' at the end instead of '*'
    private static final Pattern HTML_TITLE = Pattern.compile(
            "[\\p{L}\\p{N}\\s\\-_',:\\[\\]!\\./\\\\\\(\\)&]*");
    private static final Pattern HTML_CLASS = Pattern.compile(
            "[a-zA-Z0-9\\s,\\-_]+");

    private static final Pattern ONSITE_URL = Pattern.compile(
            "(?:[\\p{L}\\p{N}\\\\\\.\\#@\\$%\\+&;\\-_~,\\?=/!]+|\\#(\\w)+)");
    private static final Pattern OFFSITE_URL = Pattern.compile(
            "\\s*(?:(?:ht|f)tps?://|mailto:)[\\p{L}\\p{N}]"
                    + "[\\p{L}\\p{N}\\p{Zs}\\.\\#@\\$%\\+&;:\\-_~,\\?=/!\\(\\)]*+\\s*");

    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|\\.[0-9]+)");

    private static final Pattern  POSITIVE_INTEGERS= Pattern.compile(
            "\\b(?<!\\.)\\d+(?!\\.)\\b"
    );

    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9\\-_\\$]+");

    private static final Pattern ALIGN = Pattern.compile(
            "(?i)center|left|right|justify|char");

    private static final Pattern VALIGN = Pattern.compile(
            "(?i)baseline|bottom|middle|top");

    private static final Predicate<String> COLOR_NAME_OR_COLOR_CODE
            = matchesEither(COLOR_NAME, COLOR_CODE);

    private static final Predicate<String> ONSITE_OR_OFFSITE_URL
            = matchesEither(ONSITE_URL, OFFSITE_URL);

    private static final Pattern HISTORY_BACK = Pattern.compile(
            "(?:javascript:)?\\Qhistory.go(-1)\\E");

    private static final Pattern ONE_CHAR = Pattern.compile(
            ".?", Pattern.DOTALL);


    public static final PolicyFactory POLICY_DEFINITION = new HtmlPolicyBuilder()
            .allowAttributes("id").matching(HTML_ID).globally()
            .allowAttributes("class").matching(HTML_CLASS).globally()
            .allowAttributes("lang").matching(Pattern.compile("[a-zA-Z]{2,20}"))
            .globally()
            .allowAttributes("title").matching(HTML_TITLE).globally()
            .allowStyling()
            .allowAttributes("align").matching(ALIGN).onElements("p")
            .allowAttributes("for").matching(HTML_ID).onElements("label")
            .allowAttributes("color").matching(COLOR_NAME_OR_COLOR_CODE::test)
            .onElements("font")
            .allowAttributes("face")
            .matching(Pattern.compile("[\\w;, \\-]+"))
            .onElements("font")
            .allowAttributes("size").matching(NUMBER).onElements("font")
            .allowAttributes("href").matching(ONSITE_OR_OFFSITE_URL::test)
            .onElements("a")
            .allowStandardUrlProtocols()
            .allowAttributes("nohref").onElements("a")
            .allowAttributes("name").matching(NAME).onElements("a")
            .allowAttributes(
                    "onfocus", "onblur", "onclick", "onmousedown", "onmouseup")
            .matching(HISTORY_BACK).onElements("a")
            .requireRelNofollowOnLinks()
            .allowAttributes("src").matching(ONSITE_OR_OFFSITE_URL::test)
            .onElements("img")
            .allowAttributes("name").matching(NAME)
            .onElements("img")
            .allowAttributes("alt").matching(PARAGRAPH)
            .onElements("img")
            .allowAttributes("border", "hspace", "vspace").matching(NUMBER)
            .onElements("img")
            .allowAttributes("border", "cellpadding", "cellspacing")
            .matching(NUMBER).onElements("table")
            .allowAttributes("bgcolor").matching(COLOR_NAME_OR_COLOR_CODE::test)
            .onElements("table")
            .allowAttributes("background").matching(ONSITE_URL)
            .onElements("table")
            .allowAttributes("align").matching(ALIGN)
            .onElements("table")
            .allowAttributes("noresize").matching(Pattern.compile("(?i)noresize"))
            .onElements("table")
            .allowAttributes("background").matching(ONSITE_URL)
            .onElements("td", "th", "tr")
            .allowAttributes("bgcolor").matching(COLOR_NAME_OR_COLOR_CODE::test)
            .onElements("td", "th")
            .allowAttributes("abbr").matching(PARAGRAPH)
            .onElements("td", "th")
            .allowAttributes("axis", "headers").matching(NAME)
            .onElements("td", "th")
            .allowAttributes("scope")
            .matching(Pattern.compile("(?i)(?:row|col)(?:group)?"))
            .onElements("td", "th")
            .allowAttributes("nowrap")
            .onElements("td", "th")
            .allowAttributes("height", "width").matching(NUMBER_OR_PERCENT)
            .onElements("table", "td", "th", "tr", "img")
            .allowAttributes("align").matching(ALIGN)
            .onElements("thead", "tbody", "tfoot", "img",
                    "td", "th", "tr", "colgroup", "col")
            .allowAttributes("valign").matching(VALIGN)
            .onElements("thead", "tbody", "tfoot",
                    "td", "th", "tr", "colgroup", "col")
            .allowAttributes("charoff").matching(NUMBER_OR_PERCENT)
            .onElements("td", "th", "tr", "colgroup", "col",
                    "thead", "tbody", "tfoot")
            .allowAttributes("char").matching(ONE_CHAR)
            .onElements("td", "th", "tr", "colgroup", "col",
                    "thead", "tbody", "tfoot")
            .allowAttributes("colspan", "rowspan").matching(NUMBER)
            .onElements("td", "th")
            .allowAttributes("span", "width").matching(NUMBER_OR_PERCENT)
            .onElements("colgroup", "col")
            .allowAttributes("data-mention-id").matching(POSITIVE_INTEGERS).globally()
            .allowElements(
                    "a", "label", "noscript", "h1", "h2", "h3", "h4", "h5", "h6",
                    "p", "i", "b", "u", "strong", "em", "small", "big", "pre", "code",
                    "cite", "samp", "sub", "sup", "strike", "center", "blockquote",
                    "hr", "br", "col", "font", "map", "span", "div", "img",
                    "ul", "ol", "li", "dd", "dt", "dl", "tbody", "thead", "tfoot",
                    "table", "td", "th", "tr", "colgroup", "fieldset", "legend")
            .toFactory();
}
