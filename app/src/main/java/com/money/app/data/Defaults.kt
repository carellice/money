package com.money.app.data

/** Pastel palette shared by categories and fixed expenses. */
object Palette {
    const val LILAC = 0xFFB8A6FF
    const val PEACH = 0xFFFFB4A2
    const val MINT = 0xFF8FE3C0
    const val BUTTER = 0xFFFFD86E
    const val SKY = 0xFF8EC9FF
    const val ROSE = 0xFFFF9EC4
    const val LIME = 0xFFC9F27A
    const val CORAL = 0xFFFF8A7A
    const val SAND = 0xFFE8C9A0
    const val TEAL = 0xFF7FD8D8
    const val LAVENDER = 0xFFD9B8FF
    const val ORANGE = 0xFFFFB86B

    val all = listOf(LILAC, PEACH, MINT, BUTTER, SKY, ROSE, LIME, CORAL, SAND, TEAL, LAVENDER, ORANGE)
}

internal data class DefaultCategory(
    val name: String,
    val icon: String,
    val color: Long,
    val subs: List<String> = emptyList(),
)

internal val defaultExpenseCategories = listOf(
    DefaultCategory("Casa", "home", Palette.PEACH, listOf("Affitto", "Mutuo", "Bollette", "Condominio", "Manutenzione")),
    DefaultCategory("Cibo", "food", Palette.BUTTER, listOf("Supermercato", "Ristorante", "Delivery", "Bar e caffè")),
    DefaultCategory("Trasporti", "car", Palette.SKY, listOf("Carburante", "Mezzi pubblici", "Auto", "Parcheggio", "Taxi")),
    DefaultCategory("Abbonamenti", "repeat", Palette.LILAC, listOf("Streaming", "Musica", "Software", "Telefonia", "Internet")),
    DefaultCategory("Svago", "party", Palette.ROSE, listOf("Uscite", "Hobby", "Cinema ed eventi", "Giochi")),
    DefaultCategory("Viaggi", "flight", Palette.TEAL, listOf("Alloggio", "Biglietti", "Vacanza")),
    DefaultCategory("Shopping", "bag", Palette.CORAL, listOf("Abbigliamento", "Elettronica", "Arredo")),
    DefaultCategory("Salute", "health", Palette.MINT, listOf("Farmacia", "Visite", "Sport e palestra")),
    DefaultCategory("Cura personale", "spa", Palette.LAVENDER, listOf("Parrucchiere", "Cosmetici")),
    DefaultCategory("Istruzione", "school", Palette.SAND, listOf("Corsi", "Libri")),
    DefaultCategory("Tasse e assicurazioni", "receipt", Palette.ORANGE, listOf("Tasse", "Assicurazioni")),
    DefaultCategory("Regali", "gift", Palette.ROSE),
    DefaultCategory("Animali", "pets", Palette.LIME),
    DefaultCategory("Altro", "more", Palette.SAND),
)

internal val defaultIncomeCategories = listOf(
    DefaultCategory("Stipendio", "work", Palette.MINT),
    DefaultCategory("Freelance", "laptop", Palette.SKY),
    DefaultCategory("Rimborsi", "undo", Palette.BUTTER),
    DefaultCategory("Regali", "gift", Palette.ROSE),
    DefaultCategory("Investimenti", "trending", Palette.LILAC),
    DefaultCategory("Vendite", "sell", Palette.PEACH),
    DefaultCategory("Altro", "more", Palette.SAND),
)
