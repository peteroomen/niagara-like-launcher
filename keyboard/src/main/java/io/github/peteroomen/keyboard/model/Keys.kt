package io.github.peteroomen.keyboard.model

/** What a key that doesn't type a character does. */
enum class Action { SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, MORE_SYMBOLS, LETTERS, EMOJI }

/** A key, [width] in key units: a letter is 1, and every row is 10 wide. */
sealed interface Key {
    val width: Float
}

/** Types [text]. Held, it offers [alternates] (the first is picked if the finger doesn't move). */
data class CharKey(val text: String, val alternates: List<String> = emptyList(), override val width: Float = 1f) : Key

data class ActionKey(val action: Action, override val width: Float) : Key

/** A row of keys, with [indent] units of space at each end (the middle letter row). */
data class Row(val keys: List<Key>, val indent: Float = 0f) {
    val width get() = keys.sumOf { it.width.toDouble() }.toFloat() + indent * 2
}

data class Layout(val rows: List<Row>)

enum class Page { LETTERS, SYMBOLS, MORE_SYMBOLS, NUMBERS }

/** What kind of field is being typed in, which changes a key or two, or the whole layout. */
enum class FieldKind { TEXT, EMAIL, URI, NUMBER, PHONE }

object Layouts {
    const val ROW_WIDTH = 10f

    /** Held letters: the top row's number first, then accents, macrons first (te reo Māori). */
    private val letterAlternates = mapOf(
        "q" to listOf("1"),
        "w" to listOf("2"),
        "e" to listOf("3", "ē", "é", "è", "ê", "ë"),
        "r" to listOf("4"),
        "t" to listOf("5"),
        "y" to listOf("6", "ý", "ÿ"),
        "u" to listOf("7", "ū", "ú", "ù", "û", "ü"),
        "i" to listOf("8", "ī", "í", "ì", "î", "ï"),
        "o" to listOf("9", "ō", "ó", "ò", "ô", "ö", "õ", "ø", "œ"),
        "p" to listOf("0"),
        "a" to listOf("ā", "á", "à", "â", "ä", "ã", "å", "æ"),
        "s" to listOf("ß", "ś", "š"),
        "d" to listOf("ð"),
        "g" to listOf("ğ"),
        "l" to listOf("ł"),
        "z" to listOf("ž", "ź", "ż"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
    )

    private val symbolAlternates = mapOf(
        "." to listOf(",", "?", "!", "'", "\"", ":", ";", "-", "…", "@", "#"),
        "1" to listOf("¹", "½", "⅓", "¼", "⅛"),
        "2" to listOf("²", "⅔"),
        "3" to listOf("³", "¾", "⅜"),
        "0" to listOf("°", "ⁿ", "∅"),
        "$" to listOf("£", "€", "¥", "¢", "₹"),
        "-" to listOf("–", "—", "_", "·"),
        "+" to listOf("±"),
        "(" to listOf("<", "{", "["),
        ")" to listOf(">", "}", "]"),
        "'" to listOf("‘", "’", "‚", "‹", "›"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "!" to listOf("¡"),
        "?" to listOf("¿", "‽"),
        "%" to listOf("‰"),
        "*" to listOf("★", "†", "‡"),
        "/" to listOf("\\"),
        "&" to listOf("§"),
        "=" to listOf("≠", "≈", "∞"),
    )

    private fun chars(text: String) = text.map { c ->
        val s = c.toString()
        CharKey(s, letterAlternates[s] ?: symbolAlternates[s] ?: emptyList())
    }

    /** The bottom row: ?123, then (with [emojiKey]) an emoji key as in Gboard, the comma, space, full stop and Enter. */
    private fun bottomRow(page: Page, field: FieldKind, emojiKey: Boolean): Row {
        val switch = if (page == Page.LETTERS) Action.SYMBOLS else Action.LETTERS
        // Email and web addresses get the key they need most next to the space bar
        val left = when (field) {
            FieldKind.EMAIL -> CharKey("@")
            FieldKind.URI -> CharKey("/")
            else -> CharKey(",")
        }
        return Row(listOfNotNull(
            ActionKey(switch, 1.5f),
            if (emojiKey) ActionKey(Action.EMOJI, 1f) else null,
            left,
            ActionKey(Action.SPACE, if (emojiKey) 4f else 5f),
            CharKey(".", symbolAlternates["."].orEmpty()),
            ActionKey(Action.ENTER, 1.5f),
        ))
    }

    fun build(page: Page, field: FieldKind, emojiKey: Boolean = false): Layout = when (page) {
        Page.LETTERS -> Layout(listOf(
            Row(chars("qwertyuiop")),
            Row(chars("asdfghjkl"), indent = 0.5f),
            Row(listOf(ActionKey(Action.SHIFT, 1.5f)) + chars("zxcvbnm") + ActionKey(Action.BACKSPACE, 1.5f)),
            bottomRow(page, field, emojiKey),
        ))
        Page.SYMBOLS -> Layout(listOf(
            Row(chars("1234567890")),
            Row(chars("@#\$_&-+()/")),
            Row(listOf(ActionKey(Action.MORE_SYMBOLS, 1.5f)) + chars("*\"':;!?") + ActionKey(Action.BACKSPACE, 1.5f)),
            bottomRow(page, field, emojiKey),
        ))
        Page.MORE_SYMBOLS -> Layout(listOf(
            Row(chars("~`|•√π÷×¶∆")),
            Row(chars("£¢€¥^°={}\\")),
            Row(listOf(ActionKey(Action.SYMBOLS, 1.5f)) + chars("%©®™✓[]") + ActionKey(Action.BACKSPACE, 1.5f)),
            bottomRow(page, field, emojiKey),
        ))
        // A number pad: four wide columns
        Page.NUMBERS -> {
            fun key(text: String, alternates: List<String> = emptyList()) = CharKey(text, alternates, 2.5f)
            val dash = if (field == FieldKind.PHONE) key("-", listOf("+", "(", ")", "#", "*", "/", " ")) else key("-")
            Layout(listOf(
                Row(listOf(key("1"), key("2"), key("3"), dash)),
                Row(listOf(key("4"), key("5"), key("6"), ActionKey(Action.SPACE, 2.5f))),
                Row(listOf(key("7"), key("8"), key("9"), ActionKey(Action.BACKSPACE, 2.5f))),
                Row(listOf(
                    ActionKey(Action.SYMBOLS, 2.5f),
                    key("0"),
                    if (field == FieldKind.PHONE) key("+", listOf("*", "#", "(", ")")) else key(".", listOf(",")),
                    ActionKey(Action.ENTER, 2.5f),
                )),
            ))
        }
    }

    /** The page a field opens on. */
    fun firstPage(field: FieldKind) = when (field) {
        FieldKind.NUMBER, FieldKind.PHONE -> Page.NUMBERS
        else -> Page.LETTERS
    }
}
