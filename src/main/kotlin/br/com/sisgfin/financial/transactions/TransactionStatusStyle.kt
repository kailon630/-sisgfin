package br.com.sisgfin.financial.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.sisgfin.WsDanger
import br.com.sisgfin.WsSuccess
import br.com.sisgfin.WsTextDisabled
import br.com.sisgfin.WsTextPrimary
import br.com.sisgfin.WsTextSecondary
import br.com.sisgfin.WsWarning

data class StatusVisual(
    val background: Color,
    val foreground: Color,
    val label: String
)

@Composable
fun TransactionStatus.toVisual(): StatusVisual = when (this) {
    TransactionStatus.PAID -> StatusVisual(
        WsSuccess.copy(alpha = 0.15f),
        WsSuccess,
        displayName
    )
    TransactionStatus.OVERDUE -> StatusVisual(
        WsDanger.copy(alpha = 0.12f),
        WsDanger,
        displayName
    )
    TransactionStatus.PENDING -> StatusVisual(
        WsWarning.copy(alpha = 0.15f),
        WsWarning,
        displayName
    )
    TransactionStatus.PARTIAL -> StatusVisual(
        WsWarning.copy(alpha = 0.1f),
        WsWarning,
        displayName
    )
    TransactionStatus.SCHEDULED -> StatusVisual(
        Color(0xFF4C8DFF).copy(alpha = 0.12f),
        Color(0xFF7EB6FF),
        displayName
    )
    TransactionStatus.DRAFT -> StatusVisual(
        WsTextDisabled.copy(alpha = 0.2f),
        WsTextSecondary,
        displayName
    )
    TransactionStatus.CANCELED -> StatusVisual(
        WsTextDisabled.copy(alpha = 0.15f),
        WsTextDisabled,
        displayName
    )
}

@Composable
fun TransactionStatusBadge(status: TransactionStatus, modifier: Modifier = Modifier) {
    val visual = status.toVisual()
    Box(
        modifier = modifier
            .background(visual.background, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = visual.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = visual.foreground
        )
    }
}

@Composable
fun TransactionTypeLabel(type: TransactionType, modifier: Modifier = Modifier) {
    val color = when (type) {
        TransactionType.INCOME -> WsSuccess
        TransactionType.EXPENSE -> WsDanger
        TransactionType.TRANSFER -> Color(0xFF7EB6FF)
        TransactionType.ADJUSTMENT -> WsTextSecondary
        TransactionType.REVERSAL -> WsWarning
    }
    Text(
        text = type.displayName.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}
