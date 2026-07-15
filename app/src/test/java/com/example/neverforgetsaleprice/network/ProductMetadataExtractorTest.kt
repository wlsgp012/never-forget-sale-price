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

    @Test
    fun extractsJsonLdOfferPriceSpecificationBeforeStrikethroughPrice() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Product",
                  "name": "디지몬 스토리 타임 스트레인저",
                  "image": "https://example.com/digimon.jpg",
                  "offers": {
                    "@type": "Offer",
                    "priceSpecification": [
                      {
                        "@type": "UnitPriceSpecification",
                        "price": 41880,
                        "priceCurrency": "KRW"
                      },
                      {
                        "@type": "UnitPriceSpecification",
                        "priceType": "https://schema.org/StrikethroughPrice",
                        "price": 69800,
                        "priceCurrency": "KRW"
                      }
                    ]
                  }
                }
                </script>
              </head>
            </html>
        """.trimIndent()

        val metadata = extractor.extract(html, "https://www.xbox.com/ko-KR/games/store/example")

        assertEquals("디지몬 스토리 타임 스트레인저", metadata.title)
        assertEquals(41_880L, metadata.price)
        assertEquals("structured JSON-LD product data", metadata.confidenceNote)
    }

    @Test
    fun prefersCurrentSalePriceOverPreviousPriceInVisibleText() {
        val html = """
            <html>
              <head>
                <meta property="og:title" content="Xbox 세일 게임" />
              </head>
              <body>
                <div>
                  <span>이전 가격 ₩59,900</span>
                  <span>현재 가격 ₩17,970</span>
                  <span>70% 할인</span>
                </div>
              </body>
            </html>
        """.trimIndent()

        val metadata = extractor.extract(html, "https://www.xbox.com/ko-KR/games/store/example")

        assertEquals("Xbox 세일 게임", metadata.title)
        assertEquals(17_970L, metadata.price)
        assertEquals("visible page text", metadata.confidenceNote)
    }
}
