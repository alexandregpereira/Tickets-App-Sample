package com.example.cielo.purchase.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cielo.purchase.Purchase
import com.example.cielo.purchase.PurchaseStatus
import com.example.cielo.ui.formatAsBrl
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReceiptScreen(
    purchaseReference: String,
    onDoneClick: () -> Unit,
    uiModel: ReceiptUiModel = koinViewModel { parametersOf(purchaseReference) },
) {
    val state by uiModel.state.collectAsStateWithLifecycle()
    ReceiptContent(state = state, onDoneClick = onDoneClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptContent(state: ReceiptUiState, onDoneClick: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Comprovante") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val purchase = state.purchase
            if (purchase == null) {
                Text("Compra não encontrada.")
            } else {
                StatusCard(purchase)
                PurchaseSummary(purchase)
            }

            Button(onClick = onDoneClick, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar aos eventos")
            }
        }
    }
}

@Composable
private fun StatusCard(purchase: Purchase) {
    val (title, container, onContainer) = when (purchase.status) {
        PurchaseStatus.APPROVED -> Triple(
            "Pagamento aprovado",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )

        PurchaseStatus.CANCELLED -> Triple(
            "Pagamento cancelado",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )

        PurchaseStatus.DENIED -> Triple(
            "Pagamento negado",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        PurchaseStatus.PENDING -> Triple(
            "Pagamento pendente",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            purchase.failureReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (purchase.status == PurchaseStatus.APPROVED) {
                Text(
                    text = "${purchase.quantity} ingresso(s) para ${purchase.eventName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PurchaseSummary(purchase: Purchase) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Resumo da compra", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            SummaryRow("Evento", purchase.eventName)
            SummaryRow("Quantidade", purchase.quantity.toString())
            SummaryRow("Valor unitário", purchase.unitPriceInCents.formatAsBrl())
            SummaryRow("Total", purchase.totalInCents.formatAsBrl(), emphasize = true)
            purchase.paymentDescription?.let { SummaryRow("Forma de pagamento", it) }
            SummaryRow("Referência", purchase.reference)
            purchase.cieloOrderId?.takeIf { it.isNotBlank() }?.let { SummaryRow("Pedido Cielo", it) }
            purchase.authCode?.let { SummaryRow("Autorização", it) }
            purchase.cieloCode?.let { SummaryRow("Código Cielo", it) }
            purchase.brand?.let { SummaryRow("Bandeira", it) }
            purchase.maskedCard?.let { SummaryRow("Cartão", it) }
            purchase.terminal?.let { SummaryRow("Terminal", it) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else null,
            color = Color.Unspecified,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
