package com.example.neverforgetsaleprice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.neverforgetsaleprice.data.ProductEntity
import com.example.neverforgetsaleprice.domain.CheckInterval
import com.example.neverforgetsaleprice.domain.CheckIntervalUnit
import com.example.neverforgetsaleprice.domain.CheckStatus
import com.example.neverforgetsaleprice.domain.DiscountPolicy
import com.example.neverforgetsaleprice.domain.PriceFormatter
import com.example.neverforgetsaleprice.ui.AddProductState
import com.example.neverforgetsaleprice.ui.AddProductViewModel
import com.example.neverforgetsaleprice.ui.ProductDetailViewModel
import com.example.neverforgetsaleprice.ui.ProductListViewModel
import com.example.neverforgetsaleprice.ui.RepositoryViewModelFactory
import com.example.neverforgetsaleprice.worker.PriceCheckScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        val productId = intent.getLongExtra(EXTRA_PRODUCT_ID, 0L).takeIf { it > 0L }
        setContent {
            SalePriceApp(productId)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}

@Composable
private fun SalePriceApp(initialProductId: Long?) {
    val context = LocalContext.current
    val container = (context.applicationContext as SalePriceApplication).appContainer
    val navController = rememberNavController()
    val startDestination = initialProductId?.let { Routes.detail(it) } ?: Routes.List

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.List) {
                    val viewModel: ProductListViewModel = viewModel(
                        factory = RepositoryViewModelFactory {
                            ProductListViewModel(container.repository)
                        }
                    )
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    ProductListScreen(
                        products = state.products,
                        onAddClick = { navController.navigate(Routes.Add) },
                        onProductClick = { navController.navigate(Routes.detail(it.id)) }
                    )
                }
                composable(Routes.Add) {
                    val viewModel: AddProductViewModel = viewModel(
                        factory = RepositoryViewModelFactory {
                            AddProductViewModel(container.repository)
                        }
                    )
                    val state by viewModel.state
                    AddProductScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onUrlChange = viewModel::updateUrl,
                        onNameChange = viewModel::updateName,
                        onOriginalPriceChange = viewModel::updateOriginalPrice,
                        onCurrentPriceChange = viewModel::updateCurrentPrice,
                        onImageUrlChange = viewModel::updateImageUrl,
                        onCheckIntervalValueChange = viewModel::updateCheckIntervalValue,
                        onCheckIntervalUnitChange = viewModel::updateCheckIntervalUnit,
                        onFetch = viewModel::fetch,
                        onSave = {
                            viewModel.save { id ->
                                PriceCheckScheduler.scheduleNow(context.applicationContext)
                                navController.navigate(Routes.detail(id)) {
                                    popUpTo(Routes.List)
                                }
                            }
                        }
                    )
                }
                composable(
                    route = Routes.Detail,
                    arguments = listOf(navArgument("productId") { type = NavType.LongType })
                ) { entry ->
                    val productId = entry.arguments?.getLong("productId") ?: 0L
                    val viewModel: ProductDetailViewModel = viewModel(
                        key = "product-$productId",
                        factory = RepositoryViewModelFactory {
                            ProductDetailViewModel(container.repository, productId)
                        }
                    )
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    ProductDetailScreen(
                        product = state.product,
                        onBack = { navController.popBackStack() },
                        onActiveChange = { product, active -> viewModel.setActive(product, active) },
                        onDelete = { product ->
                            viewModel.delete(product) {
                                navController.navigate(Routes.List) {
                                    popUpTo(Routes.List) { inclusive = true }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductListScreen(
    products: List<ProductEntity>,
    onAddClick: () -> Unit,
    onProductClick: (ProductEntity) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Sale Price Watch") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    ) { padding ->
        if (products.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductRow(product = product, onClick = { onProductClick(product) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("등록된 상품이 없습니다.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "상품 URL을 등록하면 설정한 주기마다 할인 여부를 확인합니다.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProductRow(product: ProductEntity, onClick: () -> Unit) {
    val discount = DiscountPolicy.discountPercent(
        product.originalPrice,
        product.lastCheckedPrice ?: product.originalPrice
    )
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text("원래 가격 ${PriceFormatter.format(product.originalPrice)}")
                Text("최근 가격 ${PriceFormatter.format(product.lastCheckedPrice)}")
                Text(product.statusText())
            }
            if (discount > 0) {
                Text(
                    "$discount%",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProductScreen(
    state: AddProductState,
    onBack: () -> Unit,
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onOriginalPriceChange: (String) -> Unit,
    onCurrentPriceChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    onCheckIntervalValueChange: (String) -> Unit,
    onCheckIntervalUnitChange: (CheckIntervalUnit) -> Unit,
    onFetch: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상품 등록") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                label = { Text("상품 URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = onFetch,
                enabled = !state.isLoading && state.url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("페이지 조회")
            }
            state.confidenceNote?.let {
                Text("제안 기준: $it", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("상품명") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.originalPrice,
                onValueChange = onOriginalPriceChange,
                label = { Text("원래 가격") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = state.currentPrice,
                onValueChange = onCurrentPriceChange,
                label = { Text("현재 가격") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = state.imageUrl,
                onValueChange = onImageUrlChange,
                label = { Text("이미지 URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("조회 주기", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.checkIntervalValue,
                    onValueChange = onCheckIntervalValueChange,
                    label = { Text("값") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                CheckIntervalUnit.entries.forEach { unit ->
                    if (state.checkIntervalUnit == unit) {
                        Button(onClick = { onCheckIntervalUnitChange(unit) }) {
                            Text(unit.label)
                        }
                    } else {
                        OutlinedButton(onClick = { onCheckIntervalUnitChange(unit) }) {
                            Text(unit.label)
                        }
                    }
                }
            }
            Text(
                "저장될 주기: ${CheckInterval.format(state.checkIntervalSeconds())}",
                style = MaterialTheme.typography.bodySmall
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "저장 중" else "저장")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailScreen(
    product: ProductEntity?,
    onBack: () -> Unit,
    onActiveChange: (ProductEntity, Boolean) -> Unit,
    onDelete: (ProductEntity) -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상품 상세") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }
            )
        }
    ) { padding ->
        if (product == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("상품을 찾을 수 없습니다.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Text(product.name, style = MaterialTheme.typography.headlineSmall)
            InfoLine("원래 가격", PriceFormatter.format(product.originalPrice))
            InfoLine("최근 가격", PriceFormatter.format(product.lastCheckedPrice))
            InfoLine("할인율", "${DiscountPolicy.discountPercent(product.originalPrice, product.lastCheckedPrice ?: product.originalPrice)}%")
            InfoLine("조회 주기", CheckInterval.format(product.checkIntervalSeconds))
            InfoLine("확인 상태", product.statusText())
            InfoLine("마지막 알림", product.lastNotifiedAtMillis?.formatTime() ?: "-")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("모니터링", modifier = Modifier.weight(1f))
                Switch(
                    checked = product.isActive,
                    onCheckedChange = { onActiveChange(product, it) }
                )
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.url)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("원문 페이지 열기")
            }
            OutlinedButton(
                onClick = { onDelete(product) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("삭제")
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.65f))
    }
}

private fun ProductEntity.statusText(): String {
    return when (lastCheckStatus) {
        CheckStatus.NeverChecked -> "아직 확인 전"
        CheckStatus.Success -> "마지막 확인 ${lastCheckedAtMillis?.formatTime() ?: "-"}"
        CheckStatus.NetworkError -> "네트워크 실패"
        CheckStatus.HttpError -> "페이지 응답 실패"
        CheckStatus.ParseError -> lastCheckError ?: "가격 분석 실패"
        CheckStatus.InvalidUrl -> "URL 오류"
    }
}

private fun Long.formatTime(): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(this))
}

private object Routes {
    const val List = "list"
    const val Add = "add"
    const val Detail = "detail/{productId}"

    fun detail(productId: Long): String = "detail/$productId"
}
