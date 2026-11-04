package com.plus.browser
import org.junit.Assert.*
import org.junit.Test
class SearchUrlTest {
    @Test fun allProvidersAndCategoriesProduceHttpUrls() {
        for(engine in listOf("Google","Bing","DuckDuckGo")) for(category in SearchCategory.entries) {
            val url=searchUrl(engine,category,"hello world")
            assertTrue(url.startsWith("https://")); assertTrue(url.contains("hello+world"))
        }
    }
    @Test fun preservesPersianWithUrlEncoding() { assertTrue(searchUrl("Google",SearchCategory.IMAGES,"سلام دنیا").contains("%D8%B3%D9%84%D8%A7%D9%85"));assertTrue(searchUrl("Google",SearchCategory.IMAGES,"cat").endsWith("&tbm=isch")) }
    @Test fun routesCategoriesForBingAndDuckDuckGo() { assertTrue(searchUrl("Bing",SearchCategory.VIDEOS,"x").contains("/videos/search"));assertTrue(searchUrl("DuckDuckGo",SearchCategory.NEWS,"x").contains("ia=news")) }
}
