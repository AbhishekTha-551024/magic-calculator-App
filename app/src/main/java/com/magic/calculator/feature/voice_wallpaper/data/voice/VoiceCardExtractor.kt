package com.magic.calculator.feature.voice_wallpaper.data.voice

import java.util.Locale
import kotlin.math.min

/**
 * VoiceCardExtractor
 *
 * Extracts any of the 52 playing cards from spoken Hindi, Hinglish, or English.
 * Prioritizes multi-word phrases ("kala pan", "lal pan") and exact matches before fuzzy fallback.
 * Supports numbers as cards (e.g. 1=Ace, 11=Jack, 12=Queen, 13=King, "heart 1" -> AH, "heart 11" -> JH).
 */
object VoiceCardExtractor {

    data class PlayingCard(
        val code: String,          // e.g. "QH", "AS", "10D", "7C"
        val rank: String,          // "A", "2".."10", "J", "Q", "K"
        val suit: Suit,            // HEARTS, DIAMONDS, CLUBS, SPADES
        val englishName: String,   // "Queen of Hearts"
        val hindiName: String,     // "पान की बेगम"
        val symbol: String,        // "♥", "♦", "♣", "♠"
        val isRed: Boolean         // true for Hearts/Diamonds
    )

    enum class Suit(val code: String, val symbol: String, val isRed: Boolean, val english: String, val hindi: String) {
        HEARTS("H", "♥", true, "Hearts", "पान"),
        DIAMONDS("D", "♦", true, "Diamonds", "ईंट"),
        CLUBS("C", "♣", false, "Clubs", "चिड़ी"),
        SPADES("S", "♠", false, "Spades", "हुकुम")
    }

    private val SUIT_KEYWORDS = mapOf(
        Suit.SPADES to listOf(
            "काला पान", "कालापान", "kala pan", "kalapan", "spades", "spade", "स्पेड्स", "स्पेड", "हुकुम", "hukum", "हुकम", "hukam", "hukm"
        ),
        Suit.HEARTS to listOf(
            "लाल पान", "लालपान", "lal pan", "lalpan", "hearts", "heart", "पान", "paan", "pan", "दिल", "dil"
        ),
        Suit.DIAMONDS to listOf(
            "diamonds", "diamond", "डायमंड", "डाइमंड", "ईंट", "eent", "ईट", "int", "eeth", "inta", "brick"
        ),
        Suit.CLUBS to listOf(
            "clubs", "club", "क्लब्स", "क्लब", "चिड़िया", "चिड़ी", "chidia", "chidiya", "chidi", "chidya", "flower"
        )
    )

    private val RANK_KEYWORDS = listOf(
        Pair("K", listOf("king", "बादशाह", "badshah", "baadshah", "राजा", "raja", "किंग", "13", "thirteen", "तेरह", "k")),
        Pair("Q", listOf("queen", "बेगम", "begam", "begum", "रानी", "rani", "क्वीन", "12", "twelve", "बारह", "q")),
        Pair("J", listOf("jack", "गुलाम", "gulam", "ghulam", "joker", "जैक", "11", "eleven", "ग्यारह", "j")),
        Pair("10", listOf("ten", "10", "दहला", "dahla", "dehla", "दस", "das", "dus")),
        Pair("9", listOf("nine", "9", "नहला", "nehla", "नौ", "nau", "no")),
        Pair("8", listOf("eight", "8", "अट्ठी", "atthi", "आठ", "aath", "ath")),
        Pair("7", listOf("seven", "7", "सत्ती", "satti", "सात", "saat", "sat")),
        Pair("6", listOf("6", "six", "छक्का", "chhakki", "छह", "chhah", "chhe")),
        Pair("5", listOf("5", "five", "पंजी", "panji", "पांच", "paanch", "panch")),
        Pair("4", listOf("4", "four", "चौकी", "chauki", "chouki", "चार", "chaar", "char")),
        Pair("3", listOf("3", "three", "तिग्गी", "tigi", "तीन", "teen", "tin")),
        Pair("2", listOf("2", "two", "दुग्गी", "dugi", "duggi", "दो", "do", "deuce")),
        Pair("A", listOf("ace", "एक्का", "इक्का", "एका", "ikka", "ekka", "akka", "ikk", "one", "1", "a"))
    )

    /**
     * Extracts a PlayingCard from the raw spoken transcript.
     * Returns null if no card could be confidently identified.
     */
    fun extractCard(spokenText: String?): PlayingCard? {
        if (spokenText.isNullOrBlank()) return null
        val normalized = spokenText.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{Nd}\\s]"), " ")
            .trim()

        // 1. Direct short-code check (e.g. "QH", "AS", "10D")
        val directCard = getCardByCode(normalized.uppercase(Locale.ROOT))
        if (directCard != null) return directCard

        val words = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // 2. PASS 1A: Multi-word phrases for Suit (e.g. "kala pan", "lal pan")
        var matchedSuit: Suit? = null
        for ((suit, keywords) in SUIT_KEYWORDS) {
            for (kw in keywords) {
                val kwLower = kw.lowercase(Locale.ROOT)
                if (kwLower.contains(" ") && normalized.contains(kwLower)) {
                    matchedSuit = suit
                    break
                }
            }
            if (matchedSuit != null) break
        }

        // 2. PASS 1B: Exact single-word match for Suit
        if (matchedSuit == null) {
            for ((suit, keywords) in SUIT_KEYWORDS) {
                for (kw in keywords) {
                    val kwLower = kw.lowercase(Locale.ROOT)
                    if (!kwLower.contains(" ") && words.contains(kwLower)) {
                        matchedSuit = suit
                        break
                    }
                }
                if (matchedSuit != null) break
            }
        }

        // 3. PASS 1: Exact matches for Rank
        var matchedRank: String? = null
        for ((rankCode, keywords) in RANK_KEYWORDS) {
            for (kw in keywords) {
                val kwLower = kw.lowercase(Locale.ROOT)
                if (words.contains(kwLower)) {
                    matchedRank = rankCode
                    break
                }
            }
            if (matchedRank != null) break
        }

        // 4. PASS 2: Fuzzy matches for Suit (if not found in Pass 1)
        if (matchedSuit == null) {
            for ((suit, keywords) in SUIT_KEYWORDS) {
                for (kw in keywords) {
                    val kwLower = kw.lowercase(Locale.ROOT)
                    for (word in words) {
                        if (kwLower.length >= 4 && levenshtein(word, kwLower) <= 1) {
                            matchedSuit = suit
                            break
                        }
                    }
                    if (matchedSuit != null) break
                }
                if (matchedSuit != null) break
            }
        }

        // 5. PASS 2: Fuzzy matches for Rank (if not found in Pass 1)
        if (matchedRank == null) {
            for ((rankCode, keywords) in RANK_KEYWORDS) {
                for (kw in keywords) {
                    val kwLower = kw.lowercase(Locale.ROOT)
                    for (word in words) {
                        if (kwLower.length >= 4 && levenshtein(word, kwLower) <= 1) {
                            matchedRank = rankCode
                            break
                        }
                    }
                    if (matchedRank != null) break
                }
                if (matchedRank != null) break
            }
        }

        if (matchedSuit != null && matchedRank != null) {
            return createCard(matchedRank, matchedSuit)
        }

        // 6. Direct concatenated fallback (e.g. "queenofhearts", "lalpanbegam", "heart1", "heart11")
        for (suit in Suit.values()) {
            for ((rankCode, _) in RANK_KEYWORDS) {
                val testCode = "$rankCode${suit.code}"
                if (normalized.contains(testCode.lowercase(Locale.ROOT))) {
                    return createCard(rankCode, suit)
                }
            }
        }

        return null
    }

    fun getCardByCode(code: String): PlayingCard? {
        val clean = code.trim().uppercase(Locale.ROOT)
        if (clean.length < 2 || clean.length > 3) return null

        val suitChar = clean.last().toString()
        val rankStr = clean.substring(0, clean.length - 1)

        val suit = when (suitChar) {
            "H" -> Suit.HEARTS
            "D" -> Suit.DIAMONDS
            "C" -> Suit.CLUBS
            "S" -> Suit.SPADES
            else -> return null
        }

        val validRanks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
        if (!validRanks.contains(rankStr)) return null

        return createCard(rankStr, suit)
    }

    fun getAll52Cards(): List<PlayingCard> {
        val list = mutableListOf<PlayingCard>()
        val ranks = listOf("A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2")
        for (suit in Suit.values()) {
            for (rank in ranks) {
                list.add(createCard(rank, suit))
            }
        }
        return list
    }

    private fun createCard(rank: String, suit: Suit): PlayingCard {
        val rankEng = when (rank) {
            "A" -> "Ace"
            "K" -> "King"
            "Q" -> "Queen"
            "J" -> "Jack"
            else -> rank
        }
        val rankHi = when (rank) {
            "A" -> "एक्का"
            "K" -> "बादशाह"
            "Q" -> "बेगम"
            "J" -> "गुलाम"
            "10" -> "दहला"
            "9" -> "नहला"
            "8" -> "अट्ठी"
            "7" -> "सत्ती"
            "6" -> "छक्का"
            "5" -> "पंजी"
            "4" -> "चौकी"
            "3" -> "तिग्गी"
            "2" -> "दुग्गी"
            else -> rank
        }

        return PlayingCard(
            code = "$rank${suit.code}",
            rank = rank,
            suit = suit,
            englishName = "$rankEng of ${suit.english}",
            hindiName = "${suit.hindi} का $rankHi",
            symbol = suit.symbol,
            isRed = suit.isRed
        )
    }

    private fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
