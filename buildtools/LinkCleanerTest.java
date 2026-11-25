import io.github.bitcoinismoral.linkcleaner.LinkCleaner;

/** Plain-Java test runner (no JUnit needed). Exit code 1 on any failure. */
public class LinkCleanerTest {
    static int fails = 0, total = 0;

    static void eq(String in, String want) {
        total++;
        String got = LinkCleaner.clean(in).text;
        if (!got.equals(want)) { fails++; System.out.println("FAIL\n  in:   " + in + "\n  got:  " + got + "\n  want: " + want); }
    }
    static void same(String in) { eq(in, in); }

    public static void main(String[] a) {
        // --- X / Twitter
        eq("https://x.com/user/status/123?s=20&t=abcDEF", "https://x.com/user/status/123");
        eq("https://twitter.com/user/status/9?s=46#m", "https://twitter.com/user/status/9");
        eq("https://mobile.twitter.com/b?t=2&ref_src=twsrc", "https://mobile.twitter.com/b");
        eq("x.com/u/status/5?s=20", "x.com/u/status/5");
        same("https://notx.com/a?b=1");
        eq("https://x.com/search?q=%23GrapheneOS&src=typed_query&f=live&t=xyz", "https://x.com/search?q=%23GrapheneOS&src=typed_query&f=live");
        same("https://x.com/search?q=privacy");
        eq("https://x.com/hashtag/bitcoin?src=hashtag_click&s=20", "https://x.com/hashtag/bitcoin?src=hashtag_click");

        // --- YouTube
        eq("https://www.youtube.com/watch?v=dQw4w9WgXcQ&pp=ygUEcmljaw%3D%3D&si=abc123&t=42",
           "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42");
        eq("https://www.youtube.com/embed/M7lc1UVf-VE?si=demo&cc_load_policy=1",
           "https://www.youtube.com/embed/M7lc1UVf-VE?cc_load_policy=1");
        eq("https://youtu.be/dQw4w9WgXcQ?si=XYZ_abc", "https://youtu.be/dQw4w9WgXcQ");
        eq("https://youtu.be/dQw4w9WgXcQ?si=XYZ&t=10", "https://youtu.be/dQw4w9WgXcQ?t=10");
        eq("https://youtube.com/shorts/abc123?si=x1&feature=share", "https://youtube.com/shorts/abc123");
        eq("https://music.youtube.com/watch?v=abc&si=x&feature=share", "https://music.youtube.com/watch?v=abc");
        eq("https://www.youtube.com/playlist?list=PL123&si=abc", "https://www.youtube.com/playlist?list=PL123");
        eq("https://www.youtube.com/redirect?event=video_description&redir_token=QUFF&q=https%3A%2F%2Fexample.com%2Fpage%3Futm_source%3Dyoutube&v=abc",
           "https://example.com/page");
        eq("youtube.com/watch?v=abc&si=zzz", "youtube.com/watch?v=abc");

        // --- AI assistants citing sources
        eq("https://example.com/article?utm_source=chatgpt.com", "https://example.com/article");
        eq("https://news.site/a?id=5&utm_source=perplexity&utm_medium=ai-citation", "https://news.site/a?id=5");
        eq("https://blog.example.org/post/?utm_source=gemini", "https://blog.example.org/post/");
        eq("https://docs.example.com/guide?utm_source=copilot.com&utm_medium=ai-answer#install",
           "https://docs.example.com/guide#install");
        eq("https://site.com/page#:~:text=hello%20world", "https://site.com/page");
        eq("https://site.com/page#intro:~:text=some,text", "https://site.com/page#intro");
        eq("Sources: [1] https://a.com/x?utm_source=chatgpt.com [2] https://b.org/y?ref=abc&utm_source=chatgpt.com",
           "Sources: [1] https://a.com/x [2] https://b.org/y?ref=abc");
        eq("See [the study](https://journal.org/paper?utm_source=chatgpt.com).",
           "See [the study](https://journal.org/paper).");

        // --- generic ad / analytics / e-mail trackers
        eq("https://site.com/?fbclid=IwAR123abc", "https://site.com/");
        eq("https://shop.com/p/1?color=blue&gclid=Cj0K&msclkid=abc&ttclid=x&twclid=y", "https://shop.com/p/1?color=blue");
        eq("https://shop.com/p?UTM_Source=News&UTM_MEDIUM=email&mc_eid=8f3a&mc_cid=1&_hsenc=p2&mkt_tok=zz",
           "https://shop.com/p");
        eq("https://store.com/item?srsltid=AfmBOo&_gl=1*abc&_ga=2.1", "https://store.com/item");
        eq("https://site.com/a?gad_source=1&gbraid=0AAA&wbraid=Cj", "https://site.com/a");
        eq("https://site.com/a#utm_source=newsletter", "https://site.com/a");

        // --- Meta
        eq("https://www.instagram.com/reel/ABC123/?igsh=MWx0dGZh", "https://www.instagram.com/reel/ABC123/");
        eq("https://www.instagram.com/p/XYZ/?igshid=YmMyMTA2M2Y=&img_index=2", "https://www.instagram.com/p/XYZ/?img_index=2");
        eq("https://www.threads.net/@user/post/abc?xmt=AQGz&slof=1", "https://www.threads.net/@user/post/abc");
        eq("https://www.facebook.com/share/v/abc/?mibextid=w8EBqM", "https://www.facebook.com/share/v/abc/");
        eq("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F%3Ffbclid%3Dabc&h=AT0x",
           "https://example.com/");

        // --- TikTok / Reddit / LinkedIn / Spotify / Apple
        eq("https://www.tiktok.com/@user/video/7123?is_from_webapp=1&sender_device=pc&_r=1&_t=8abc",
           "https://www.tiktok.com/@user/video/7123");
        eq("https://www.reddit.com/r/android/comments/abc/title/?share_id=XYZ&utm_medium=android_app&utm_name=androidcss",
           "https://www.reddit.com/r/android/comments/abc/title/");
        eq("https://www.reddit.com/r/a/comments/b/c/?context=3&share_id=1", "https://www.reddit.com/r/a/comments/b/c/?context=3");
        eq("https://www.linkedin.com/posts/someone_activity-123?utm_source=share&utm_medium=member_android&rcm=ACoAA",
           "https://www.linkedin.com/posts/someone_activity-123");
        eq("https://www.linkedin.com/jobs/view/123/?trk=public_jobs&trackingId=abc&refId=x", "https://www.linkedin.com/jobs/view/123/");
        eq("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=a1b2c3", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC");
        eq("https://music.apple.com/us/album/x/123?i=456&uo=4&at=1000l&ct=abc", "https://music.apple.com/us/album/x/123?i=456");

        // --- shopping
        eq("https://www.amazon.com/Some-Product/dp/B0123ABC/ref=sr_1_1?crid=2X&keywords=foo&qid=1700&sr=8-1",
           "https://www.amazon.com/Some-Product/dp/B0123ABC?keywords=foo");
        eq("https://www.amazon.co.uk/dp/B0X/ref=cm_sw_r_cp?pd_rd_w=a&pf_rd_p=b&th=1&psc=1&tag=aff-21",
           "https://www.amazon.co.uk/dp/B0X?th=1&psc=1");
        eq("https://www.ebay.com/itm/1234?_trkparms=abc&_trksid=p123&hash=item1&var=55", "https://www.ebay.com/itm/1234?var=55");

        // --- search engines & redirects
        eq("https://www.google.com/search?q=cats&sca_esv=1a&ei=XYZ&ved=0ah&oq=cats&gs_lp=Egx&sclient=gws",
           "https://www.google.com/search?q=cats");
        eq("https://www.google.com/url?sa=t&rct=j&url=https%3A%2F%2Fexample.com%2Fx%3Futm_source%3Dgoogle%26id%3D7&ved=2ah",
           "https://example.com/x?id=7");
        eq("https://www.bing.com/search?q=dogs&form=QBLH&sp=-1&cvid=abc", "https://www.bing.com/search?q=dogs");
        eq("https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2F%3Futm_campaign%3Dx&rut=abc", "https://example.com/");
        eq("https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fdoc%3Fmc_eid%3D1&data=05",
           "https://example.com/doc");

        // --- news & newsletters
        eq("https://www.nytimes.com/2026/01/01/tech/x.html?smid=nytcore-ios-share&sgrp=c-cb",
           "https://www.nytimes.com/2026/01/01/tech/x.html");
        eq("https://www.nytimes.com/2026/01/01/x.html?unlocked_article_code=1.abc&smid=url-share",
           "https://www.nytimes.com/2026/01/01/x.html?unlocked_article_code=1.abc");
        eq("https://someone.substack.com/p/post?r=2abc&utm_campaign=post&utm_medium=web&triedRedirect=true",
           "https://someone.substack.com/p/post");
        eq("https://medium.com/@a/title-123?source=user_profile", "https://medium.com/@a/title-123");

        // --- functional params that must survive on big sites
        same("https://www.facebook.com/groups/123/?ref=share_group_link_x_app");
        same("https://www.amazon.com/dp/B0X?pd=1&pf=2");
        same("https://www.reddit.com/r/a/?sh=1");
        same("https://apps.apple.com/app/id123?ls=1");
        // --- must stay untouched
        same("https://example.com/search?q=test&page=2");
        same("https://example.com/?s=20");
        same("https://github.com/user/repo/blob/main/file.java#L10");
        same("https://en.wikipedia.org/wiki/Android_(operating_system)");
        same("https://maps.google.com/?q=coffee");
        same("https://www.google.com/maps/place/x/@1,2,3z");
        same("Just some text with no links at all.");
        same("email me at someone@x.com please");
        eq("Wiki: https://en.wikipedia.org/wiki/Foo_(bar)?utm_source=x, nice", "Wiki: https://en.wikipedia.org/wiki/Foo_(bar), nice");

        // --- counts
        LinkCleaner.Result r = LinkCleaner.clean("https://x.com/a?s=1 and https://example.com/b and https://youtu.be/c?si=d");
        total++;
        if (r.linksFound != 3 || r.linksCleaned != 2) { fails++; System.out.println("FAIL counts " + r.linksFound + "/" + r.linksCleaned); }

        System.out.println((total - fails) + "/" + total + " link tests passed");
        fails += StripperTest.run();
        if (fails > 0) System.exit(1);
    }
}
