@file:Suppress("unused") // every registered native has to exist, used or not

package io.github.peteroomen.keyboard.engine

/**
 * The native side of the suggestion engine: AOSP LatinIME's natives (src/main/cpp/latinime),
 * registered on these classes when the library loads. Each signature must match the C++
 * registration exactly, or loading fails, so they're declared as AOSP declares them, used or not.
 */
internal object NativeLibrary {
    /** True once loaded: the natives below are only callable after this. */
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("jni_latinime") }.isSuccess }
}

/** AOSP's BinaryDictionary natives. */
internal object NativeDictionary {
    @JvmStatic external fun openNative(sourceDir: String, dictOffset: Long, dictSize: Long, isUpdatable: Boolean): Long
    @JvmStatic external fun createOnMemoryNative(formatVersion: Long, locale: String, attributeKeys: Array<String>, attributeValues: Array<String>): Long
    @JvmStatic external fun closeNative(dict: Long)
    @JvmStatic external fun getFormatVersionNative(dict: Long): Int
    @JvmStatic external fun getHeaderInfoNative(dict: Long, outHeaderSize: IntArray, outFormatVersion: IntArray, outAttributeKeys: ArrayList<IntArray>, outAttributeValues: ArrayList<IntArray>)
    @JvmStatic external fun flushNative(dict: Long, filePath: String): Boolean
    @JvmStatic external fun needsToRunGCNative(dict: Long, mindsBlockByGC: Boolean): Boolean
    @JvmStatic external fun flushWithGCNative(dict: Long, filePath: String): Boolean
    @JvmStatic external fun getSuggestionsNative(
        dict: Long,
        proximityInfo: Long,
        traverseSession: Long,
        xCoordinates: IntArray,
        yCoordinates: IntArray,
        times: IntArray,
        pointerIds: IntArray,
        inputCodePoints: IntArray,
        inputSize: Int,
        suggestOptions: IntArray,
        prevWordCodePointArrays: Array<IntArray?>,
        isBeginningOfSentenceArray: BooleanArray,
        prevWordCount: Int,
        outSuggestionCount: IntArray,
        outCodePoints: IntArray,
        outScores: IntArray,
        outSpaceIndices: IntArray,
        outTypes: IntArray,
        outAutoCommitFirstWordConfidence: IntArray,
        inOutWeightOfLangModelVsSpatialModel: FloatArray,
    )
    @JvmStatic external fun getProbabilityNative(dict: Long, word: IntArray): Int
    @JvmStatic external fun getMaxProbabilityOfExactMatchesNative(dict: Long, word: IntArray): Int
    @JvmStatic external fun getNgramProbabilityNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray): Int
    @JvmStatic external fun getWordPropertyNative(
        dict: Long,
        word: IntArray,
        isBeginningOfSentence: Boolean,
        outCodePoints: IntArray,
        outFlags: BooleanArray,
        outProbabilityInfo: IntArray,
        outNgramPrevWordsArray: ArrayList<*>,
        outNgramPrevWordIsBeginningOfSentenceArray: ArrayList<*>,
        outNgramTargets: ArrayList<*>,
        outNgramProbabilityInfo: ArrayList<*>,
        outShortcutTargets: ArrayList<*>,
        outShortcutProbabilities: ArrayList<*>,
    )
    @JvmStatic external fun getNextWordNative(dict: Long, token: Int, outCodePoints: IntArray, outIsBeginningOfSentence: BooleanArray): Int
    @JvmStatic external fun addUnigramEntryNative(
        dict: Long,
        word: IntArray,
        probability: Int,
        shortcutTarget: IntArray?,
        shortcutProbability: Int,
        isBeginningOfSentence: Boolean,
        isNotAWord: Boolean,
        isPossiblyOffensive: Boolean,
        timestamp: Int,
    ): Boolean
    @JvmStatic external fun removeUnigramEntryNative(dict: Long, word: IntArray): Boolean
    @JvmStatic external fun addNgramEntryNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray, probability: Int, timestamp: Int): Boolean
    @JvmStatic external fun removeNgramEntryNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray): Boolean
    @JvmStatic external fun updateEntriesForWordWithNgramContextNative(
        dict: Long,
        prevWordCodePointArrays: Array<IntArray?>,
        isBeginningOfSentenceArray: BooleanArray,
        word: IntArray,
        isValidWord: Boolean,
        count: Int,
        timestamp: Int,
    ): Boolean
    @JvmStatic external fun getPropertyNative(dict: Long, query: String): String?
    @JvmStatic external fun isCorruptedNative(dict: Long): Boolean
    @JvmStatic external fun migrateNative(dict: Long, dictFilePath: String, newFormatVersion: Long): Boolean
}

/** AOSP's BinaryDictionaryUtils natives. */
internal object NativeDictionaryUtils {
    @JvmStatic external fun createEmptyDictFileNative(filePath: String, dictVersion: Long, locale: String, attributeKeys: Array<String>, attributeValues: Array<String>): Boolean
    @JvmStatic external fun calcNormalizedScoreNative(before: IntArray, after: IntArray, score: Int): Float
    @JvmStatic external fun setCurrentTimeForTestNative(currentTime: Int): Int
}

/** AOSP's DicTraverseSession natives: working memory for one dictionary's searches. */
internal object NativeSession {
    @JvmStatic external fun setDicTraverseSessionNative(locale: String, dictSize: Long): Long
    @JvmStatic external fun initDicTraverseSessionNative(session: Long, dictionary: Long, previousWord: IntArray?, previousWordLength: Int)
    @JvmStatic external fun releaseDicTraverseSessionNative(session: Long)
}

/** AOSP's ProximityInfo natives: where the keys are, so the engine knows which ones a finger could have meant. */
internal object NativeProximity {
    @JvmStatic external fun setProximityInfoNative(
        displayWidth: Int,
        displayHeight: Int,
        gridWidth: Int,
        gridHeight: Int,
        mostCommonKeyWidth: Int,
        mostCommonKeyHeight: Int,
        proximityChars: IntArray,
        keyCount: Int,
        keyXCoordinates: IntArray,
        keyYCoordinates: IntArray,
        keyWidths: IntArray,
        keyHeights: IntArray,
        keyCharCodes: IntArray,
        sweetSpotCenterXs: FloatArray?,
        sweetSpotCenterYs: FloatArray?,
        sweetSpotRadii: FloatArray?,
    ): Long
    @JvmStatic external fun releaseProximityInfoNative(info: Long)
}
