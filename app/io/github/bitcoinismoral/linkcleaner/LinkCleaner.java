package io.github.bitcoinismoral.linkcleaner;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes link-level tracking from every URL found in a piece of text.
 *
 *  1. Unwraps tracking redirects (google.com/url, l.facebook.com, youtube.com/redirect, ...).
 *  2. Site rules for the big platforms (X, YouTube, TikTok, Amazon, Google, Meta, Reddit, ...).
 *  3. Global tracker list on every site: utm_* (incl. AI tags such as utm_source=chatgpt.com,
 *     perplexity, gemini, copilot), ad click IDs (fbclid, gclid, msclkid, ttclid, ...),
 *     e-mail marketing IDs (mc_eid, _hsenc, mkt_tok, ...).
 *  4. Text fragments (#:~:text=...) added by Google Search / AI Overviews when citing a passage.
 * Functional parameters (search terms, video IDs, timestamps, page numbers, ...) are kept.
 * Everything runs offline; nothing is ever fetched.
 */
public final class LinkCleaner {

    private LinkCleaner() {}

    public static final class Result {
        public final String text;
        public final int linksCleaned;
        public final int linksFound;
        Result(String text, int linksCleaned, int linksFound) {
            this.text = text; this.linksCleaned = linksCleaned; this.linksFound = linksFound;
        }
    }

    // http(s) URLs, plus bare X / YouTube links people paste without a scheme.
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://[^\\s<>\"'`]+|\\b(?:(?:www|m|mobile|music)\\.)?"
            + "(?:x\\.com|twitter\\.com|youtu\\.be|youtube\\.com)/[^\\s<>\"'`]*)");

    private static Set<String> set(String... s) { return new HashSet<>(Arrays.asList(s)); }

    /** Removed on every site (compared lower-case). */
    private static final Set<String> GLOBAL = set(
            // ad click IDs
            "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "gad_source", "gad_campaignid",
            "msclkid", "yclid", "ysclid", "twclid", "ttclid", "li_fat_id", "sccid", "tblci", "ndclid",
            "epik", "rb_clickid", "irclickid", "s_kwcid", "ef_id",
            // analytics / cross-site
            "_ga", "_gl", "srsltid", "igshid", "igsh", "ref_src", "ref_url",
            "fb_action_ids", "fb_action_types", "fb_ref", "fb_source",
            "wt_mc", "wt.mc_id", "wt.mc_ev", "wt.srch", "wt_zmc", "s_cid", "cmpid", "ncid", "sr_share",
            "at_medium", "at_campaign", "at_custom1", "at_custom2", "at_custom3", "at_custom4",
            "pk_campaign", "pk_kwd", "pk_source", "pk_medium", "pk_content",
            "mtm_campaign", "mtm_kwd", "mtm_source", "mtm_medium", "mtm_content", "mtm_cid",
            "mtm_group", "mtm_placement",
            "hmb_campaign", "hmb_medium", "hmb_source", "mbid",
            // e-mail / CRM marketing
            "mc_cid", "mc_eid", "_hsenc", "_hsmi", "__hstc", "__hssc", "__hsfp", "hsctatracking",
            "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "__s", "ck_subscriber_id",
            "_bta_tid", "_bta_c", "trk_contact", "sms_source", "sms_click", "sms_uph",
            "spjobid", "spmailingid", "spreportid", "spuserid", "_kx", "mkevt");

    private static final Set<String> X_SEARCH_KEEP = set("q", "f", "src", "pf", "lf");
    private static final Set<String> YT_KEEP = set("v", "t", "list", "index", "start", "end",
            "time_continue", "search_query", "c");
    private static final Set<String> YT_EMBED_KEEP = new HashSet<>(YT_KEEP);
    static { YT_EMBED_KEEP.add("cc_load_policy"); }

    private static final Set<String> GOOGLE_SEARCH_KEEP = set("q", "tbm", "udm", "start", "hl", "tbs",
            "num", "safe", "lr", "gl");
    private static final Set<String> BING_KEEP = set("q", "first", "setlang", "cc");
    private static final Set<String> AMAZON = set("ref", "ref_", "tag", "linkcode", "linkid", "ascsubtag",
            "creative", "creativeasin", "camp", "content-id", "_encoding", "qid", "sr", "sprefix", "crid",
            "dib", "dib_tag", "dchild", "social_share", "starsleft", "skiptwisterog", "spia", "spla",
            "sp_csd", "plattr", "th_ref");
    private static final Set<String> EBAY = set("_trkparms", "_trksid", "hash", "mkcid", "mkrid", "campid",
            "toolid", "customid", "amdata", "_from", "itmmeta", "itmprp", "mkscid");
    private static final Set<String> SPOTIFY = set("si", "nd", "context", "flow_ctx", "_branch_match_id",
            "_branch_referrer", "pt", "pi");
    private static final Set<String> META = set("mibextid", "sfnsn", "rdid", "share_url", "__tn__", "xmt",
            "slof", "paipv", "sfns", "extid");
    private static final Set<String> REDDIT = set("share_id", "rdt", "ref", "ref_source", "ref_campaign",
            "$deep_link", "$3p", "_branch_match_id", "_branch_referrer", "correlation_id", "post_fullname",
            "post_index", "utm_name", "web3x");
    private static final Set<String> LINKEDIN = set("trk", "trkinfo", "trackingid", "refid", "lipi", "rcm",
            "midtoken", "midsig", "eid", "otptoken", "original_referer", "lgcta", "lgtemp");
    private static final Set<String> APPLE = set("at", "ct", "uo", "itscg", "itsct");
    private static final Set<String> MEDIUM = set("source");
    private static final Set<String> NYT = set("smid", "sgrp", "partner", "campaign_id", "emc", "instance_id",
            "nl", "regi_id", "segment_id", "te", "user_id");
    private static final Set<String> SUBSTACK = set("r", "triedredirect", "showwelcomeonshare", "isfreemail");
    private static final Set<String> PINTEREST = set("invite_code", "sender", "sfo", "mweb_unauth_id");
    private static final Set<String> NONE = Collections.emptySet();

    public static Result clean(CharSequence input) {
        if (input == null) return new Result("", 0, 0);
        String s = input.toString();
        Matcher m = URL.matcher(s);
        StringBuilder out = new StringBuilder();
        int last = 0, cleaned = 0, found = 0;
        while (m.find()) {
            int start = m.start();
            String raw = m.group();
            if (!raw.regionMatches(true, 0, "http", 0, 4) && start > 0) {
                char b = s.charAt(start - 1);
                if (Character.isLetterOrDigit(b) || b == '.' || b == '-' || b == '@' || b == '/') continue;
            }
            String url = trimTrailing(raw);
            if (url.isEmpty()) continue;
            String c = cleanUrl(url, 0);
            found++;
            if (!c.equals(url)) cleaned++;
            out.append(s, last, start).append(c);
            last = start + url.length();
        }
        out.append(s.substring(last));
        return new Result(out.toString(), cleaned, found);
    }

    /** Drops punctuation that ends a sentence or closes brackets around a link. */
    static String trimTrailing(String u) {
        int end = u.length();
        while (end > 0) {
            char c = u.charAt(end - 1);
            if (".,;:!?'\"*".indexOf(c) >= 0) { end--; continue; }
            if ((c == ')' && count(u, '(', end) < count(u, ')', end))
                    || (c == ']' && count(u, '[', end) < count(u, ']', end))
                    || (c == '}' && count(u, '{', end) < count(u, '}', end))) { end--; continue; }
            break;
        }
        return u.substring(0, end);
    }

    private static int count(String s, char ch, int end) {
        int n = 0;
        for (int i = 0; i < end; i++) if (s.charAt(i) == ch) n++;
        return n;
    }

    /** Cleans one complete URL (as received from a tapped link), without trimming punctuation. */
    public static String cleanSingleUrl(String url) {
        if (url == null) return null;
        try { return cleanUrl(url.trim(), 0); } catch (RuntimeException e) { return url; }
    }

    static String cleanUrl(String url, int depth) {
        int se = url.indexOf("://");
        String scheme = se > 0 ? url.substring(0, se + 3) : "";
        String rest = se > 0 ? url.substring(se + 3) : url;

        String fragment = null;
        int h = rest.indexOf('#');
        if (h >= 0) { fragment = rest.substring(h + 1); rest = rest.substring(0, h); }
        String query = null;
        int q = rest.indexOf('?');
        if (q >= 0) { query = rest.substring(q + 1); rest = rest.substring(0, q); }
        int slash = rest.indexOf('/');
        String authority = slash >= 0 ? rest.substring(0, slash) : rest;
        String path = slash >= 0 ? rest.substring(slash) : "";
        String host = authority.toLowerCase(Locale.ROOT);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);

        // 1) tracking redirects -> the real destination
        if (depth < 3 && query != null) {
            String target = redirectTarget(host, path, query);
            if (target != null) return cleanUrl(target, depth + 1);
        }

        // 2) site rules
        boolean dropQuery = false, dropFragment = false;
        Set<String> keepOnly = null, extra = NONE;
        String extraPrefix = null;
        String newPath = path;
        if (is(host, "x.com", "twitter.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com",
                "twittpr.com")) {
            // Status/profile links carry only tracking (?s=, &t=, ref_src); search & hashtag pages need q/f.
            if (path.startsWith("/search") || path.startsWith("/hashtag/")) keepOnly = X_SEARCH_KEEP;
            else dropQuery = true;
            dropFragment = true;
        } else if (is(host, "tiktok.com")) {
            dropQuery = true;
        } else if (is(host, "youtube.com", "youtu.be", "youtube-nocookie.com")) {
            keepOnly = path.startsWith("/embed/") ? YT_EMBED_KEEP : YT_KEEP;
        } else if (isGoogle(host) && path.equals("/search")) {
            keepOnly = GOOGLE_SEARCH_KEEP;
        } else if (is(host, "bing.com") && path.equals("/search")) {
            keepOnly = BING_KEEP;
        } else if (isBrand(host, "amazon")) {
            newPath = path.replaceAll("/ref=[^/]*", "");
            extra = AMAZON; extraPrefix = "pd_rd_|pf_rd_";
        } else if (isBrand(host, "ebay")) {
            extra = EBAY;
        } else if (is(host, "spotify.com", "spotify.link")) {
            extra = SPOTIFY;
        } else if (is(host, "instagram.com", "threads.net", "threads.com", "facebook.com", "fb.watch", "fb.com",
                "messenger.com")) {
            extra = META; extraPrefix = "__cft__";
        } else if (is(host, "reddit.com", "redd.it")) {
            extra = REDDIT;
        } else if (is(host, "linkedin.com")) {
            extra = LINKEDIN;
        } else if (is(host, "apple.com")) {
            extra = APPLE;
        } else if (is(host, "medium.com")) {
            extra = MEDIUM;
        } else if (is(host, "nytimes.com")) {
            extra = NYT;
        } else if (is(host, "substack.com")) {
            extra = SUBSTACK;
        } else if (isBrand(host, "pinterest") || is(host, "pin.it")) {
            extra = PINTEREST;
        }

        // 3) query parameters
        boolean removed = false;
        List<String> kept = new ArrayList<>();
        if (query != null) {
            for (String p : query.split("&", -1)) {
                if (p.isEmpty()) continue;
                if (dropQuery) { removed = true; continue; }
                int eq = p.indexOf('=');
                String name = decode(eq >= 0 ? p.substring(0, eq) : p).toLowerCase(Locale.ROOT);
                boolean drop;
                if (keepOnly != null) drop = !keepOnly.contains(name);
                else drop = isGlobalTracker(name) || extra.contains(name)
                        || (extraPrefix != null && startsWithAny(name, extraPrefix));
                if (drop) removed = true; else kept.add(p);
            }
        }

        // 4) fragment
        String newFragment = fragment;
        if (fragment != null) {
            if (dropFragment) newFragment = null;
            else {
                int t = fragment.indexOf(":~:");
                if (t >= 0) newFragment = fragment.substring(0, t);
                String lf = newFragment.toLowerCase(Locale.ROOT);
                if (lf.startsWith("utm_") || lf.startsWith("xtor=") || lf.startsWith("at_medium=")) newFragment = "";
            }
        }

        boolean fragmentChanged = fragment != null && !fragment.equals(newFragment);
        if (!removed && newPath.equals(path) && !fragmentChanged) return url;

        StringBuilder b = new StringBuilder(scheme).append(authority).append(newPath);
        if (!kept.isEmpty()) b.append('?').append(join(kept));
        else if (query != null && !removed) b.append('?').append(query);
        if (newFragment != null && !newFragment.isEmpty()) b.append('#').append(newFragment);
        return b.toString();
    }

    private static String redirectTarget(String host, String path, String query) {
        String key = null;
        if (isGoogle(host) && path.equals("/url")) key = "q|url";
        else if (is(host, "l.facebook.com", "lm.facebook.com", "l.messenger.com") && path.startsWith("/l.php")) key = "u";
        else if (is(host, "l.instagram.com", "l.threads.net", "l.threads.com")) key = "u";
        else if (is(host, "youtube.com") && path.equals("/redirect")) key = "q";
        else if (is(host, "out.reddit.com")) key = "url";
        else if (host.endsWith("safelinks.protection.outlook.com")) key = "url";
        else if (is(host, "steamcommunity.com") && path.startsWith("/linkfilter")) key = "url|u";
        else if (is(host, "duckduckgo.com") && path.startsWith("/l/")) key = "uddg";
        else if (is(host, "linkedin.com") && path.startsWith("/redir/redirect")) key = "url";
        if (key == null) return null;
        for (String p : query.split("&")) {
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            String name = p.substring(0, eq);
            for (String k : key.split("\\|")) {
                if (name.equals(k)) {
                    String v = decode(p.substring(eq + 1));
                    if (v.regionMatches(true, 0, "http://", 0, 7) || v.regionMatches(true, 0, "https://", 0, 8)) return v;
                }
            }
        }
        return null;
    }

    private static boolean isGlobalTracker(String name) {
        return name.startsWith("utm_") || GLOBAL.contains(name);
    }

    private static boolean startsWithAny(String name, String prefixes) {
        for (String p : prefixes.split("\\|")) if (name.startsWith(p)) return true;
        return false;
    }

    /** host equals one of the domains or is a subdomain of it. */
    private static boolean is(String host, String... domains) {
        for (String d : domains) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    private static final Pattern GOOGLE_HOST = Pattern.compile("^(?:www\\.)?google\\.(?:[a-z]{2,3})(?:\\.[a-z]{2})?$");
    private static boolean isGoogle(String host) { return GOOGLE_HOST.matcher(host).matches(); }

    /** amazon.com, www.amazon.co.uk, smile.amazon.de, ... */
    private static boolean isBrand(String host, String brand) {
        return host.matches("^(?:[a-z0-9-]+\\.)*" + brand + "\\.(?:[a-z]{2,3})(?:\\.[a-z]{2})?$");
    }

    private static String decode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) b.append('&'); b.append(parts.get(i)); }
        return b.toString();
    }
}
