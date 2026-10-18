package io.github.peteroomen.keyboard.engine

import android.content.Context
import io.github.peteroomen.keyboard.model.Context as WordContext
import io.github.peteroomen.keyboard.model.EngineKey
import io.github.peteroomen.keyboard.model.Prediction
import java.io.File

/** What the engine found for a word being typed, best first, and the fix a space should put in, if any. */
data class Found(val words: List<String>, val autocorrect: String?)

/**
 * Suggestions, autocorrect, next-word prediction and learning, from AOSP LatinIME's engine (see
 * src/main/cpp/latinime): the bundled English dictionary, plus a dictionary of your own words that
 * learns what you type, as AOSP's user history does. Not thread-safe: the keyboard calls it from
 * one background thread.
 */
class Engine private constructor(private val main: Long, private val historyFile: File, private var history: Long) {

    private val mainSession = session(main)
    private var historySession = session(history)
    private var proximity = 0L

    // Reused buffers, sized as the engine requires
    private val outCount = IntArray(1)
    private val outCodePoints = IntArray(MAX_WORD_LENGTH * MAX_RESULTS)
    private val outScores = IntArray(MAX_RESULTS)
    private val outSpaceIndices = IntArray(MAX_RESULTS)
    private val outTypes = IntArray(MAX_RESULTS)
    private val outConfidence = IntArray(1)
    private val weight = FloatArray(1)
    private var learnedSinceFlush = 0

    /** Where the letter keys are now, so the engine knows which a finger could have meant. */
    fun setKeys(keys: List<EngineKey>, width: Int, height: Int, keyWidth: Int, keyHeight: Int) {
        if (proximity != 0L) NativeProximity.releaseProximityInfoNative(proximity)
        proximity = 0L
        if (keys.isEmpty() || width <= 0 || height <= 0) return
        proximity = NativeProximity.setProximityInfoNative(
            width, height, Prediction.GRID_WIDTH, Prediction.GRID_HEIGHT, keyWidth, keyHeight,
            Prediction.proximityGrid(keys, width, height, keyWidth),
            keys.size,
            keys.map { it.box.left.toInt() }.toIntArray(),
            keys.map { it.box.top.toInt() }.toIntArray(),
            keys.map { it.box.width.toInt() }.toIntArray(),
            keys.map { it.box.height.toInt() }.toIntArray(),
            keys.map { it.code }.toIntArray(),
            null, null, null,
        )
    }

    /**
     * Suggestions for [typed], where each letter was touched at [xs], [ys] (keyboard pixels, or
     * -1 where not known), after [context]. A fix goes in only if its normalised score is at least
     * [threshold] (AOSP's autocorrect strength).
     */
    fun suggest(typed: String, xs: IntArray, ys: IntArray, context: WordContext, threshold: Float): Found {
        if (typed.isEmpty()) return Found(emptyList(), null)
        val codePoints = typed.codePoints().toArray()
        if (codePoints.size > MAX_WORD_LENGTH) return Found(emptyList(), null)
        val fromMain = query(main, mainSession, codePoints, xs, ys, context, fullEditDistance = false)
        val fromHistory = query(history, historySession, codePoints, xs, ys, context, fullEditDistance = true)
        // Both dictionaries' words by score, without repeats
        val merged = (fromMain + fromHistory).sortedByDescending { it.score }.distinctBy { it.word }
        val known = isKnown(typed)
        val first = merged.firstOrNull { !it.word.equals(typed, ignoreCase = true) }
        // Autocorrect as AOSP does: only for a word it may correct, to a fix marked fit for it whose
        // score, normalised against what was typed, clears the threshold
        val fix = first?.takeIf {
            threshold != Float.MAX_VALUE &&
                Prediction.mayAutocorrect(typed, known) &&
                merged.firstOrNull() == it &&
                it.type and KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION != 0 &&
                NativeDictionaryUtils.calcNormalizedScoreNative(codePoints, it.word.codePoints().toArray(), it.score) >= threshold
        }?.word
        return Found(merged.map { it.word }, fix)
    }

    /** The likeliest next words after [context], best first. */
    fun predict(context: WordContext): List<String> {
        val empty = IntArray(0)
        val fromMain = query(main, mainSession, empty, empty, empty, context, fullEditDistance = false)
        val fromHistory = query(history, historySession, empty, empty, empty, context, fullEditDistance = true)
        return (fromMain + fromHistory).sortedByDescending { it.score }.map { it.word }.distinct()
    }

    /** In either dictionary (your own words count once you've used them). */
    fun isKnown(word: String): Boolean {
        val codePoints = word.codePoints().toArray()
        val lower = word.lowercase().codePoints().toArray()
        return listOf(main, history).any { dict ->
            NativeDictionary.getProbabilityNative(dict, codePoints) != NOT_A_PROBABILITY ||
                NativeDictionary.getProbabilityNative(dict, lower) != NOT_A_PROBABILITY
        }
    }

    /**
     * Learns that [word] was typed after [context]: [deliberate] when it was chosen on purpose (an
     * autocorrect undone, a suggestion tapped), so it counts as a real word straight away.
     */
    fun learn(word: String, context: WordContext, deliberate: Boolean) {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return
        val (prev, starts) = ngram(context)
        val valid = deliberate || isMain(word)
        NativeDictionary.updateEntriesForWordWithNgramContextNative(
            history, prev, starts, word.codePoints().toArray(), valid, 1, (System.currentTimeMillis() / 1000).toInt(),
        )
        if (++learnedSinceFlush >= 20) flush()
    }

    /** Saves what's been learned. */
    fun flush() {
        learnedSinceFlush = 0
        historyFile.parentFile?.mkdirs()
        if (NativeDictionary.needsToRunGCNative(history, false)) {
            NativeDictionary.flushWithGCNative(history, historyFile.path)
        } else {
            NativeDictionary.flushNative(history, historyFile.path)
        }
    }

    fun close() {
        flush()
        if (proximity != 0L) NativeProximity.releaseProximityInfoNative(proximity)
        NativeSession.releaseDicTraverseSessionNative(mainSession)
        NativeSession.releaseDicTraverseSessionNative(historySession)
        NativeDictionary.closeNative(main)
        NativeDictionary.closeNative(history)
    }

    private fun isMain(word: String) =
        NativeDictionary.getProbabilityNative(main, word.codePoints().toArray()) != NOT_A_PROBABILITY ||
            NativeDictionary.getProbabilityNative(main, word.lowercase().codePoints().toArray()) != NOT_A_PROBABILITY

    private class Result(val word: String, val score: Int, val type: Int)

    private fun query(dict: Long, session: Long, codePoints: IntArray, xs: IntArray, ys: IntArray, context: WordContext, fullEditDistance: Boolean): List<Result> {
        val size = codePoints.size
        val input = IntArray(MAX_WORD_LENGTH) { NOT_A_CODE }
        codePoints.copyInto(input)
        val options = IntArray(OPTIONS_SIZE).apply {
            this[OPTION_USE_FULL_EDIT_DISTANCE] = if (fullEditDistance) 1 else 0
            this[OPTION_BLOCK_OFFENSIVE_WORDS] = 1
            this[OPTION_WEIGHT_FOR_LOCALE] = 1000
        }
        val (prev, starts) = ngram(context)
        weight[0] = NOT_A_WEIGHT
        NativeDictionary.getSuggestionsNative(
            dict, proximity, session,
            xs.copyOf(maxOf(size, 1)), ys.copyOf(maxOf(size, 1)), IntArray(maxOf(size, 1)), IntArray(maxOf(size, 1)),
            input, size, options, prev, starts, prevCount(context),
            outCount, outCodePoints, outScores, outSpaceIndices, outTypes, outConfidence, weight,
        )
        return (0 until outCount[0]).mapNotNull { i ->
            val start = i * MAX_WORD_LENGTH
            var length = 0
            while (length < MAX_WORD_LENGTH && outCodePoints[start + length] != 0) length++
            if (length == 0) null else Result(String(outCodePoints, start, length), outScores[i], outTypes[i])
        }.filter { it.type and KIND_FLAG_POSSIBLY_OFFENSIVE == 0 }
    }

    /** The context as the engine takes it: previous words (most recent first), with a sentence start marked after them. */
    private fun ngram(context: WordContext): Pair<Array<IntArray?>, BooleanArray> {
        val prev = arrayOfNulls<IntArray>(Prediction.MAX_PREVIOUS)
        val starts = BooleanArray(Prediction.MAX_PREVIOUS)
        context.previous.take(Prediction.MAX_PREVIOUS).forEachIndexed { i, word -> prev[i] = word.codePoints().toArray() }
        val n = context.previous.size.coerceAtMost(Prediction.MAX_PREVIOUS)
        if (context.sentenceStart && n < Prediction.MAX_PREVIOUS) {
            prev[n] = IntArray(0)
            starts[n] = true
        }
        return prev to starts
    }

    private fun prevCount(context: WordContext): Int {
        val n = context.previous.size.coerceAtMost(Prediction.MAX_PREVIOUS)
        return if (context.sentenceStart && n < Prediction.MAX_PREVIOUS) n + 1 else n
    }

    companion object {
        private const val MAX_WORD_LENGTH = 48
        private const val MAX_RESULTS = 18
        private const val NOT_A_CODE = -1
        private const val NOT_A_PROBABILITY = -1
        private const val NOT_A_WEIGHT = -1f
        private const val FORMAT_VERSION_4 = 403L

        private const val OPTIONS_SIZE = 5
        private const val OPTION_USE_FULL_EDIT_DISTANCE = 1
        private const val OPTION_BLOCK_OFFENSIVE_WORDS = 2
        private const val OPTION_WEIGHT_FOR_LOCALE = 4

        private const val KIND_FLAG_POSSIBLY_OFFENSIVE = 0x80000000.toInt()
        private const val KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION = 0x10000000

        private fun session(dict: Long): Long =
            NativeSession.setDicTraverseSessionNative("en", 0L).also { NativeSession.initDicTraverseSessionNative(it, dict, null, 0) }

        /**
         * Opens the bundled English dictionary (read straight from the APK, which stores it
         * uncompressed) and your own words. Null if the engine can't run on this phone.
         */
        fun open(context: Context): Engine? {
            if (!NativeLibrary.loaded) return null
            return runCatching {
                context.assets.openFd("main_en.dict").use { fd ->
                    open(context.applicationInfo.sourceDir, fd.startOffset, fd.length, File(context.filesDir, "dicts/user_history_en"))
                }
            }.getOrNull()
        }

        /** Opens the dictionary at [offset] and [length] in [path], with your own words in [historyFile]. */
        internal fun open(path: String, offset: Long, length: Long, historyFile: File): Engine? {
            return runCatching {
                val main = NativeDictionary.openNative(path, offset, length, false)
                if (main == 0L) return null
                val file = historyFile
                var history = if (file.exists()) NativeDictionary.openNative(file.path, 0, file.length(), true) else 0L
                if (history == 0L || NativeDictionary.isCorruptedNative(history)) {
                    if (history != 0L) NativeDictionary.closeNative(history)
                    file.deleteRecursively()
                    val keys = arrayOf("dictionary", "locale", "version", "USES_FORGETTING_CURVE", "HAS_HISTORICAL_INFO")
                    val values = arrayOf("user_history:en", "en", (System.currentTimeMillis() / 1000).toString(), "1", "1")
                    history = NativeDictionary.createOnMemoryNative(FORMAT_VERSION_4, "en", keys, values)
                }
                if (history == 0L) {
                    NativeDictionary.closeNative(main)
                    return null
                }
                Engine(main, file, history)
            }.getOrNull()
        }
    }
}
