package com.videoget.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadsAnalyzerTest {
    @Test
    fun selectsRequestedPostInsteadOfFirstVideoOnPage() {
        val html = page(
            post("OTHER", "wrong.mp4"),
            post("TARGET", "right.mp4"),
        )

        val result = ThreadsAnalyzer.parsePage(html, "https://www.threads.com/@creator/post/TARGET")

        assertEquals("caption TARGET", result.title)
        assertEquals(1, result.formats.size)
        assertTrue(result.formats.single().directUrl!!.contains("right.mp4"))
    }

    @Test(expected = IllegalStateException::class)
    fun neverFallsBackToUnrelatedRecommendedPost() {
        ThreadsAnalyzer.parsePage(
            page(post("OTHER", "wrong.mp4")),
            "https://www.threads.com/@creator/post/MISSING",
        )
    }

    @Test
    fun resolvesShareLinkAndReturnsCarouselVideos() {
        val carousel = """
            {
              "code":"REALCODE",
              "user":{"username":"creator"},
              "caption":{"text":"carousel"},
              "carousel_media":[${media("part1.mp4", 720, 1280)},${media("part2.mp4", 480, 852)}]
            }
        """.trimIndent()
        val html = """
            <meta property="og:url" content="https://www.threads.com/@creator/post/REALCODE" />
            <script type="application/json" data-sjs>{"data":$carousel}</script>
        """.trimIndent()

        val result = ThreadsAnalyzer.parsePage(html, "https://www.threads.com/share/abc123")

        assertEquals(2, result.formats.size)
        assertTrue(result.formats[1].label.startsWith("视频 2"))
    }

    @Test
    fun resolvesShareLinkWhenMetaAttributesAreReorderedAndEscaped() {
        val html = """
            <meta content="https://www.threads.com/@creator/post/REALCODE?x=1&amp;y=2" data-extra="yes" property="og:url">
            <script type="application/json" data-sjs>{"data":${post("REALCODE", "right.mp4")}}</script>
        """.trimIndent()

        val result = ThreadsAnalyzer.parsePage(html, "https://www.threads.com/share/BAWuk1zgHY")

        assertEquals("caption REALCODE", result.title)
        assertTrue(result.formats.single().directUrl!!.contains("right.mp4"))
    }

    @Test
    fun resolvesXmtSharePageByInjectedMediaIdAndShortcode() {
        val html = """
            <a href="/?xmt=TOKEN&amp;injected_media_ids=[%223987285589003269850%22]">target</a>
            <script type="application/json" data-sjs>
              {"data":{"pk":"3987285589003269850","shortcode":"NEWCODE","user":{"username":"creator"},
              "caption":{"text":"xmt target"},"video_url":"https://scontent.cdninstagram.com/o1/v/xmt.mp4?oe=1"}}
            </script>
        """.trimIndent()

        val result = ThreadsAnalyzer.parsePage(
            html,
            "https://www.threads.com/share/BArflMFLDl/",
            "https://www.threads.com/?xmt=TOKEN&injected_media_ids=%5B%223987285589003269850%22%5D",
        )

        assertEquals("xmt target", result.title)
        assertTrue(result.formats.single().directUrl!!.contains("xmt.mp4"))
    }

    @Test(expected = IllegalStateException::class)
    fun refusesRecommendationVideoWhenXmtTargetCannotBeVerified() {
        ThreadsAnalyzer.parsePage(
            page(post("RECOMMENDED", "wrong.mp4")),
            "https://www.threads.com/share/BArflMFLDl/",
            "https://www.threads.com/?xmt=TOKEN",
        )
    }

    @Test(expected = IllegalStateException::class)
    fun ignoresUnboundInjectedMediaIdFromRecommendationMarkup() {
        val html = """
            <a href="/?injected_media_ids=[%223987285589003269850%22]">recommendation</a>
            <script type="application/json" data-sjs>
              {"data":{"pk":"3987285589003269850","code":"WRONG","user":{"username":"other"},
              "video_versions":[{"url":"https://scontent.cdninstagram.com/o1/v/wrong.mp4"}]}}
            </script>
        """.trimIndent()

        ThreadsAnalyzer.parsePage(
            html,
            "https://www.threads.com/share/BArflMFLDl/",
            "https://www.threads.com/?xmt=TARGET_TOKEN",
        )
    }

    @Test(expected = IllegalStateException::class)
    fun distinguishesMatchedImagePostFromMissingPost() {
        val html = """
            <meta property="og:url" content="https://www.threads.com/@creator/post/IMAGEONLY">
            <script type="application/json" data-sjs>
              {"data":{"code":"IMAGEONLY","user":{"username":"creator"},"caption":{"text":"image"},
              "image_versions2":{"candidates":[{"url":"https://scontent.cdninstagram.com/image.jpg"}]}}}
            </script>
        """.trimIndent()

        ThreadsAnalyzer.parsePage(html, "https://www.threads.com/share/image")
    }

    @Test
    fun extractsVideoFromNestedQuotedPost() {
        val html = """
            <script type="application/json" data-sjs>
              {"data":{"code":"TARGET","user":{"username":"creator"},"caption":{"text":"quoted"},
              "text_post_app_info":{"quoted_post":{"original_media":{"original_width":1080,"original_height":1920,
              "video_versions":[{"type":101,"url":"https://scontent.cdninstagram.com/o1/v/nested.mp4?oe=1"}]}}}}}
            </script>
        """.trimIndent()

        val result = ThreadsAnalyzer.parsePage(html, "https://www.threads.com/@creator/post/TARGET")

        assertEquals("quoted", result.title)
        assertTrue(result.formats.single().directUrl!!.contains("nested.mp4"))
        assertEquals("1080×1920", result.formats.single().label)
    }

    @Test
    fun extractsNestedCarouselAndDeduplicatesUrls() {
        val html = """
            <script type="application/json" data-sjs>
              {"data":{"code":"TARGET","user":{"username":"creator"},"caption":{"text":"nested carousel"},
              "media":{"carousel_media":[
                {"video_url":"https://scontent.cdninstagram.com/o1/v/one.mp4?one=1"},
                {"video_versions":[{"url":"https://scontent.cdninstagram.com/o1/v/one.mp4?two=2"}]},
                {"playback_url":"https://video.cdninstagram.com/o1/v/two.mp4"}
              ]}}}
            </script>
        """.trimIndent()

        val result = ThreadsAnalyzer.parsePage(html, "https://www.threads.com/@creator/post/TARGET")

        assertEquals(2, result.formats.size)
        assertTrue(result.formats[1].label.startsWith("视频 2"))
    }

    @Test
    fun extractsVideoBaseUrlFromDashManifest() {
        val manifest = """
            &lt;MPD&gt;&lt;Period&gt;&lt;AdaptationSet mimeType=&quot;video/mp4&quot;&gt;
            &lt;Representation width=&quot;720&quot; height=&quot;1280&quot;&gt;
            &lt;BaseURL&gt;https://scontent.cdninstagram.com/o1/v/dash.mp4?x=1&amp;amp;y=2&lt;/BaseURL&gt;
            &lt;/Representation&gt;&lt;/AdaptationSet&gt;&lt;/Period&gt;&lt;/MPD&gt;
        """.trimIndent()
        val html = """
            <script type="application/json" data-sjs>
              {"data":{"code":"TARGET","user":{"username":"creator"},"caption":{"text":"dash"},
              "video_dash_manifest":${jsonString(manifest)}}}
            </script>
        """.trimIndent()

        val result = ThreadsAnalyzer.parsePage(html, "https://www.threads.com/@creator/post/TARGET")

        assertTrue(result.formats.single().directUrl!!.contains("dash.mp4"))
    }

    private fun page(vararg posts: String): String = """
        <html><body>
        <script type="application/json" data-sjs>{"data":{"posts":[${posts.joinToString()}]}}</script>
        </body></html>
    """.trimIndent()

    private fun post(code: String, file: String): String = """
        {
          "code":"$code",
          "user":{"username":"creator"},
          "caption":{"text":"caption $code"},
          "original_width":720,
          "original_height":1280,
          "video_versions":[{"type":101,"url":"https://scontent.cdninstagram.com/o1/v/$file?oe=1"}]
        }
    """.trimIndent()

    private fun media(file: String, width: Int, height: Int): String = """
        {
          "code":"part",
          "original_width":$width,
          "original_height":$height,
          "video_versions":[{"type":101,"url":"https://scontent.cdninstagram.com/o1/v/$file?oe=1"}]
        }
    """.trimIndent()

    private fun jsonString(value: String): String = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value)
}
