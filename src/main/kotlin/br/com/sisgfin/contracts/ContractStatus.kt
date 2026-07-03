package br.com.sisgfin.contracts

import androidx.compose.ui.graphics.Color

enum class ContractStatus(val displayName: String, val color: Color) {
    VIGENTE("Vigente",    Color(0xFF3FB950)),
    ENCERRADO("Encerrado", Color(0xFF57606A)),
    SUSPENSO("Suspenso",  Color(0xFFE6A817)),
    CANCELADO("Cancelado", Color(0xFFFF5D73))
}
