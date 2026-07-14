# Never Forget Sale Price

상품 페이지를 등록해두면 앱이 주기적으로 가격을 확인하고, 원래 가격보다 할인 중일 때 Android 알림으로 알려주는 Kotlin Android 앱입니다.

## 주요 기능

- 상품 URL 등록
- URL 조회 시 상품명, 현재 가격, 이미지 URL 자동 제안
- 조회된 현재 가격을 원래 가격에도 자동 입력
- 등록 전 상품명, 원래 가격, 현재 가격, 이미지 URL 직접 수정
- 상품별 조회 주기 설정
  - 초, 분, 시간 단위 지원
  - 기본값은 6시간
- 등록 상품 목록 확인
  - 상품명
  - 원래 가격
  - 최근 확인 가격
  - 할인율
  - 마지막 확인 상태
- 상품 상세 화면
  - 원문 페이지 열기
  - 모니터링 켜기/끄기
  - 원래 가격 및 조회 주기 수정
  - 상품 삭제
- 로컬 JSON 백업/복원
  - 등록 상품 목록 export
  - JSON 파일 import
  - 같은 URL이 있으면 기존 상품 업데이트
- 할인 알림
  - 현재 가격이 원래 가격보다 낮을 때 알림
  - 이전에 알림 보낸 가격/할인율과 달라졌을 때만 다시 알림
  - 앱 성격에 맞춘 알림 아이콘과 큰 아이콘 적용

## 동작 방식

앱은 서버 없이 기기 내부에서 동작합니다.

- 상품 데이터는 Room(SQLite)에 저장됩니다.
- 상품 페이지 조회는 OkHttp로 수행합니다.
- HTML 분석은 Jsoup 기반 휴리스틱 파서가 처리합니다.
- JSON-LD, Open Graph, meta 태그, visible text 순서로 상품명/가격/이미지를 찾습니다.
- 백그라운드 확인은 WorkManager로 예약합니다.
- 각 상품의 `checkIntervalSeconds`를 기준으로 확인 대상 여부를 판단합니다.

JavaScript 실행이 필요한 쇼핑몰 페이지는 첫 MVP에서 자동 가격 추출이 실패할 수 있습니다. 이 경우 앱에서 가격을 직접 입력해 등록할 수 있습니다.

## 프로젝트 구조

```text
app/src/main/java/com/example/neverforgetsaleprice
├── data        # Room, DAO, Repository, JSON import/export
├── domain      # 가격 정규화, 할인 규칙, 조회 주기 모델
├── network     # URL fetch, 상품 메타데이터 추출
├── ui          # ViewModel
├── worker      # WorkManager, Android 알림
├── MainActivity.kt
└── SalePriceApplication.kt
```

## 필요 환경

- Android Studio
- Android SDK 35
- JDK 17
- Kotlin 2.0.21
- Gradle Wrapper 포함

앱 설정:

- `minSdk`: 26
- `targetSdk`: 35
- `applicationId`: `com.example.neverforgetsaleprice`

## 빌드 방법

Android Studio에서:

1. 프로젝트 열기
2. Gradle Sync 완료 대기
3. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

CLI에서:

```bash
./gradlew assembleDebug
```

생성 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 테스트

```bash
./gradlew testDebugUnitTest
```

현재 포함된 테스트:

- 가격 문자열 정규화
- 할인율 및 알림 조건
- JSON-LD/meta 기반 상품 메타데이터 추출

## 권한

- `INTERNET`: 상품 페이지 조회
- `POST_NOTIFICATIONS`: Android 13 이상 알림 표시

알림 권한을 거부해도 가격 확인과 로컬 상태 업데이트는 계속 동작합니다.

## JSON 백업 형식

앱의 내보내기 기능은 다음 형태의 JSON 파일을 생성합니다.

```json
{
  "version": 1,
  "exportedAtMillis": 1720000000000,
  "products": [
    {
      "url": "https://example.com/product",
      "name": "상품명",
      "originalPrice": 100000,
      "checkIntervalSeconds": 21600,
      "imageUrl": "https://example.com/image.jpg",
      "isActive": true,
      "lastCheckedPrice": 80000,
      "lastCheckedAtMillis": 1720000000000,
      "lastCheckStatus": "Success"
    }
  ]
}
```

가져오기 시 같은 URL의 상품이 이미 있으면 기존 상품을 업데이트하고, 없으면 새 상품으로 추가합니다.

## 제한 사항

- 서버 동기화는 없습니다.
- 가격 히스토리 차트는 아직 없습니다.
- 쇼핑몰별 전용 파서는 아직 없습니다.
- Android 백그라운드 정책 때문에 초 단위 조회 주기는 정확한 실시간 실행을 보장하지 않습니다.
