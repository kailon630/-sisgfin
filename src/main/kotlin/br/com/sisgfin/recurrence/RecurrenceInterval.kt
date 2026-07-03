package br.com.sisgfin.recurrence

enum class RecurrenceInterval(val displayName: String, val months: Long) {
    SEMANAL("Semanal", 0),        // especial: usa dias, não meses
    QUINZENAL("Quinzenal", 0),    // especial: usa dias, não meses
    MENSAL("Mensal", 1),
    BIMESTRAL("Bimestral", 2),
    TRIMESTRAL("Trimestral", 3),
    SEMESTRAL("Semestral", 6),
    ANUAL("Anual", 12)
}
