package com.example.neverforgetsaleprice.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductMetadataExtractorTest {
    private val extractor = ProductMetadataExtractor()

    @Test
    fun extractsJsonLdProduct() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Product",
                  "name": "테스트 신발",
                  "image": "https://example.com/shoe.jpg",
                  "offers": {
                    "@type": "Offer",
                    "price": "59900"
                  }
                }
                </script>
              </head>
            </html>
        """.trimIndent()

        val metadata = extractor.extract(html, "https://example.com/product")

        assertEquals("테스트 신발", metadata.title)
        assertEquals(59_900L, metadata.price)
        assertEquals("https://example.com/shoe.jpg", metadata.imageUrl)
        assertEquals("structured JSON-LD product data", metadata.confidenceNote)
    }

    @Test
    fun fallsBackToMetaTags() {
        val html = """
            <html>
              <head>
                <meta property="og:title" content="메타 상품" />
                <meta property="og:image" content="https://example.com/meta.jpg" />
                <meta property="product:price:amount" content="34900" />
              </head>
            </html>
        """.trimIndent()

        val metadata = extractor.extract(html, "https://example.com/product")

        assertEquals("메타 상품", metadata.title)
        assertEquals(34_900L, metadata.price)
        assertEquals("https://example.com/meta.jpg", metadata.imageUrl)
        assertEquals("price-like meta tag", metadata.confidenceNote)
    }
}
