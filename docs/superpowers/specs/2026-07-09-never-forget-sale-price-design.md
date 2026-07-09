# Never Forget Sale Price Design

## Summary

Build a Kotlin Android MVP that lets a user register product pages, stores the watched products locally, checks the pages every 6 hours, and sends an Android notification when a product becomes cheaper than its original price and the discount meaningfully changes.

The MVP is local-only. It does not use a backend server or cloud sync. Product data, price state, and notification state are stored on-device with Room over SQLite.

## Goals

- Let the user register a product URL.
- Fetch the page during registration and suggest likely product metadata.
- Let the user review and edit the suggested product name, original price, current price, and image URL before saving.
- Re-check active products every 6 hours.
- Notify only when the current price is below the original price and the observed sale state has changed since the last notification.
- Keep working when a page cannot be fetched or parsed by recording the failure on that product.

## Non-Goals

- No backend service, login, account sync, or push notification server.
- No complete price history chart in the first MVP.
- No site-specific parser matrix for individual shopping malls in the first MVP.
- No guarantee that every arbitrary product URL can be parsed automatically.

## User Experience

The app opens to a watched product list. Each row shows the product name, original price, last checked price, discount percentage when discounted, and the last checked time or failure status. A floating add action opens the product registration screen.

On the registration screen, the user enters a URL and taps a fetch action. The app retrieves the page and suggests:

- product name
- current price
- image URL when available

The user can edit the suggested values and must provide an original price before saving. If a current price is available during registration, it is saved as the initial `lastCheckedPrice` with the registration fetch time as `lastCheckedAtMillis`. If metadata extraction fails, the user can still save the product manually with URL, product name, and original price.

Each product detail screen shows the saved URL, image, original price, latest checked price, latest check status, last notification state, and controls to open the page, pause monitoring, edit fields, or delete the product.

## Architecture

Use a standard Kotlin Android architecture:

- Jetpack Compose for UI.
- ViewModel and Kotlin Flow for screen state.
- Room for local persistence.
- Repository layer to coordinate local data and network parsing.
- OkHttp for HTTP requests.
- Jsoup for HTML parsing.
- WorkManager for periodic background checks.
- Android notification APIs for local notifications.

The app remains usable without a server. Network access happens only when the user fetches a URL or when WorkManager checks registered products.

## Data Model

Use one Room entity for the MVP:

```kotlin
ProductEntity(
    id: Long,
    url: String,
    name: String,
    originalPrice: Long,
    imageUrl: String?,
    isActive: Boolean,
    lastCheckedPrice: Long?,
    lastCheckedAtMillis: Long?,
    lastCheckStatus: String,
    lastCheckError: String?,
    lastNotifiedPrice: Long?,
    lastNotifiedDiscountPercent: Int?,
    lastNotifiedAtMillis: Long?,
    createdAtMillis: Long,
    updatedAtMillis: Long
)
```

Prices are stored as integer minor-free KRW-style amounts for the MVP. The parser removes currency symbols and grouping separators before converting to `Long`. The first MVP assumes a single display currency per product and does not convert currencies.

Validation rules:

- URL must be an absolute `http` or `https` URL.
- Product name must not be blank.
- Original price must be greater than zero.
- Current price, when entered manually or parsed from a page, must be greater than zero.

## Page Fetching And Price Extraction

The metadata extractor accepts a URL and returns a result containing candidates for title, price, image URL, and confidence notes.

Extraction order:

1. Parse JSON-LD product data when present.
2. Parse Open Graph and common meta tags for title and image.
3. Search common price-related meta tags and attributes.
4. Search visible text for currency-like values and choose likely current price candidates.

When multiple prices are found, the app prefers structured data over free text. If the result is uncertain, the UI still shows the candidate but leaves the user in control before saving.

The parser should not execute JavaScript in the MVP. Pages that require client-side rendering may need manual entry or later site-specific support.

## Background Checks

WorkManager schedules one periodic worker with a 6-hour repeat interval. The worker:

1. Loads active products from Room.
2. Fetches each product URL.
3. Extracts the current price.
4. Updates `lastCheckedPrice`, `lastCheckedAtMillis`, and check status.
5. Decides whether a notification should be sent.
6. Updates notification state after a notification is successfully issued.

The worker should process products sequentially in the MVP to keep behavior simple and avoid aggressive traffic against shopping sites.

## Notification Rules

A product is considered on sale when:

```text
currentPrice < originalPrice
```

The discount percentage is:

```text
((originalPrice - currentPrice) * 100 / originalPrice).toInt()
```

Send a notification only when all of these are true:

- the product is active
- the current price was parsed successfully
- the current price is lower than the original price
- the current price differs from `lastNotifiedPrice`, or the discount percentage differs from `lastNotifiedDiscountPercent`

The notification title contains the product name. The notification body contains the discount percentage and current sale price. Tapping the notification opens the product detail screen when possible, with a fallback action to open the original URL.

## Failure Handling

Network failure, invalid URL, HTTP error, timeout, parser failure, and missing price are recorded per product. A failed check does not erase the last successful checked price and does not reset notification state.

The list and detail screens show the latest failure state compactly so the user can decide whether to edit the product or ignore a temporary issue.

## Permissions

The app needs internet access for fetching pages. On Android 13 and newer, it requests notification permission before posting notifications. If notification permission is denied, the app still checks prices and updates local product status.

## Testing Strategy

Unit tests cover:

- price string normalization
- discount percentage calculation
- notification decision rules
- parser behavior with sample JSON-LD, Open Graph, meta tag, and visible text fixtures
- repository behavior for success and failure updates

Room DAO tests cover insert, update, active product loading, and notification state updates.

Worker tests cover a successful discounted product, a non-discounted product, a changed discount notification, a repeated unchanged discount without notification, and a parser failure.

Compose UI tests cover the product list empty state, registration fetch-success flow, manual fallback entry flow, and product detail status display.

## Implementation Boundary

The first implementation should create a working Android project from this empty repository and deliver the MVP flow end to end. It should favor clear, testable modules over broad shopping-site coverage. Site-specific parsers and price history charts can be added later after the local MVP proves useful.
