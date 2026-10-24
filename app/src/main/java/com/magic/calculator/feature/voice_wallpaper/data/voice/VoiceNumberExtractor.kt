package com.magic.calculator.feature.voice_wallpaper.data.voice

import java.util.Locale
import kotlin.math.min

/**
 * VoiceNumberExtractor
 *
 * Extracts 1-100 numeric values from multilingual speech transcripts.
 * Implements exact regex parsing, spaced-digit parsing, English compound parsing,
 * and fuzzy Levenshtein-distance matching against Hindi and Hinglish dictionaries
 * so imprecise ASR transcriptions (e.g. "tirsat", "chohater", "sixti three") match accurately.
 */
object VoiceNumberExtractor {

    private val directDigitsRegex = Regex("\\b\\d+\\b")

    val englishUnits = mapOf(
        "zero" to 0, "oh" to 0, "o" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19
    )

    val englishTens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    val hindiScriptMap = mapOf(
        "शून्य" to 0, "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5, "पांच" to 5,
        "छह" to 6, "छः" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10,
        "ग्यारह" to 11, "बारह" to 12, "तेरह" to 13, "चौदह" to 14, "पंद्रह" to 15,
        "सोलह" to 16, "सत्रह" to 17, "अठारह" to 18, "उन्नीस" to 19, "बीस" to 20,
        "इक्कीस" to 21, "बाईस" to 22, "तेईस" to 23, "चौबीस" to 24, "पच्चीस" to 25,
        "छब्बीस" to 26, "सत्ताईस" to 27, "अट्ठाईस" to 28, "उनतीस" to 29, "तीस" to 30,
        "इकतीस" to 31, "बत्तीस" to 32, "तैंतीस" to 33, "चौंतीस" to 34, "पैंतीस" to 35,
        "छत्तीस" to 36, "सैंतीस" to 37, "अड़तीस" to 38, "उनतालीस" to 39, "चालीस" to 40,
        "इकतालीस" to 41, "बयालीस" to 42, "तैंतालीस" to 43, "चौवालीस" to 44, "पैंतालीस" to 45,
        "छियालीस" to 46, "सैंतालीस" to 47, "अड़तालीस" to 48, "उनचास" to 49, "पचास" to 50,
        "इक्यावन" to 51, "बावन" to 52, "तिरेपन" to 53, "तिरपन" to 53, "चौवन" to 54, "पचपन" to 55,
        "छप्पन" to 56, "सत्तावन" to 57, "अट्ठावन" to 58, "उनसठ" to 59, "साठ" to 60,
        "इकसठ" to 61, "बासठ" to 62, "तिरसठ" to 63, "तिरेसठ" to 63, "त्रेसठ" to 63, "तरेसठ" to 63,
        "चौंसठ" to 64, "पैंसठ" to 65, "छियासठ" to 66, "सरसठ" to 67, "अड़सठ" to 68, "उनहत्तर" to 69, "सत्तर" to 70,
        "इकहत्तर" to 71, "बहत्तर" to 72, "तिहत्तर" to 73, "चौहत्तर" to 74, "चोहत्तर" to 74, "चौहतर" to 74,
        "पचहत्तर" to 75, "छिहत्तर" to 76, "सतहत्तर" to 77, "अठहत्तर" to 78, "उन्नासी" to 79, "उनासी" to 79,
        "अस्सी" to 80, "इक्यासी" to 81, "बयासी" to 82, "तिरासी" to 83, "तिर्यासी" to 83,
        "चौरासी" to 84, "पचासी" to 85, "पिचासी" to 85, "छियासी" to 86, "सतासी" to 87, "सताशी" to 87,
        "अठासी" to 88, "अठ्ठासी" to 88, "नवासी" to 89, "नब्बे" to 90,
        "इक्यानवे" to 91, "बानवे" to 92, "तिरानवे" to 93, "चौरानवे" to 94, "पंचानवे" to 95,
        "छियानवे" to 96, "सत्तानवे" to 97, "अट्ठानवे" to 98, "निन्यानवे" to 99, "सौ" to 100
    )

    val hinglishPhoneticMap = mapOf(
        "zero" to 0, "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "saat" to 7, "sat" to 7, "aath" to 8, "ath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "barah" to 12, "terah" to 13, "chaudah" to 14, "pandrah" to 15,
        "solah" to 16, "satrah" to 17, "atharah" to 18, "unnees" to 19, "bees" to 20,
        "ikkees" to 21, "baees" to 22, "teees" to 23, "chaubees" to 24, "pachhees" to 25,
        "chhabees" to 26, "sattaees" to 27, "atthaees" to 28, "unattees" to 29, "tees" to 30,
        "ikattees" to 31, "battees" to 32, "taintees" to 33, "chauntees" to 34, "paintees" to 35,
        "chhattees" to 36, "saintees" to 37, "adtees" to 38, "untalees" to 39, "chalees" to 40,
        "iktalees" to 41, "bayalees" to 42, "taintalees" to 43, "chauwalees" to 44, "paintalees" to 45,
        "chhiyalees" to 46, "saintalees" to 47, "adtalees" to 48, "unchaas" to 49, "pachas" to 50,
        "ikyavan" to 51, "baavan" to 52, "tirepan" to 53, "tirpan" to 53, "chauvan" to 54, "pachpan" to 55,
        "chhappan" to 56, "sattavan" to 57, "atthavan" to 58, "unsath" to 59, "saath" to 60,
        "iksath" to 61, "baasath" to 62, "tirsath" to 63, "tireesath" to 63, "tresath" to 63, "tirseth" to 63,
        "chaunsath" to 64, "painsath" to 65, "chhiyasath" to 66, "sarsath" to 67, "adsath" to 68,
        "unhattar" to 69, "sattar" to 70, "ikhattar" to 71, "bahattar" to 72, "tihattar" to 73,
        "chauhattar" to 74, "chohattar" to 74, "chauhatter" to 74, "74" to 74,
        "pachhattar" to 75, "chhihattar" to 76, "satattar" to 77, "athattar" to 78, "unasi" to 79, "unnasi" to 79,
        "assi" to 80, "ekasi" to 81, "biyasi" to 82, "tirasi" to 83, "tiryasi" to 83, "chaurasi" to 84,
        "pachasi" to 85, "pichasi" to 85, "chhiyasi" to 86, "satasi" to 87, "athasi" to 88,
        "navasi" to 89, "nabbe" to 90, "ikyanve" to 91, "banve" to 92, "tiranve" to 93,
        "chauranve" to 94, "panchanve" to 95, "chhiyanve" to 96, "sattanve" to 97, "atthanve" to 98,
        "ninyanve" to 99, "sau" to 100
    )

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    fun levenshteinDistance(s1: CharSequence, s2: CharSequence): Int {
        val len0 = s1.length + 1
        val len1 = s2.length + 1

        var cost = IntArray(len0) { it }
        var newCost = IntArray(len0)

        for (i in 1 until len1) {
            newCost[0] = i
            for (j in 1 until len0) {
                val match = if (s1[j - 1] == s2[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[len0 - 1]
    }

    private val sortedHindiScriptEntries by lazy {
        hindiScriptMap.entries.sortedByDescending { it.key.length }
    }

    private val sortedHinglishEntries by lazy {
        hinglishPhoneticMap.entries.sortedByDescending { it.key.length }
    }

    /**
     * Extracts a numeric string (e.g. "63", "74", "85") from spoken phrase.
     * Returns null if no valid number is found.
     */
    fun extractNumber(spokenText: String): String? {
        val cleanText = spokenText.trim().lowercase(Locale.ROOT)
        if (cleanText.isEmpty()) return null

        // 1. Direct multi-digit numbers inside speech (e.g. "63", "74", "100")
        val match = directDigitsRegex.find(cleanText)
        if (match != null && match.value.length >= 2) {
            return match.value
        }

        // 2. Check for spaced digits (e.g. "6 3", "7 4")
        val spacedDigits = extractSpacedDigits(cleanText)
        if (spacedDigits != null) {
            return spacedDigits
        }

        // 3. Exact Hindi direct word match (sorted by length descending to match longest compound first)
        for ((word, num) in sortedHindiScriptEntries) {
            if (cleanText.contains(word)) {
                return num.toString()
            }
        }

        val tokens = cleanText.replace("-", " ")
            .replace(",", " ")
            .replace(".", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        // 4. Exact Hinglish phonetic word match (e.g. "tirsath" -> 63, "chauhattar" -> 74)
        for (token in tokens) {
            val num = hinglishPhoneticMap[token]
            if (num != null) {
                return num.toString()
            }
        }

        // 5. English word parsing (exact & fuzzy compound, e.g. "sixty three" -> 63)
        val englishNum = parseEnglishTokens(tokens)
        if (englishNum != null) {
            return englishNum
        }

        // 6. Fuzzy Levenshtein match against Hindi script & Hinglish dictionaries
        val fuzzyMatch = fuzzyMatchToken(tokens)
        if (fuzzyMatch != null) {
            return fuzzyMatch
        }

        // 7. Single digit direct match fallback
        if (match != null) {
            return match.value
        }

        return null
    }

    private fun extractSpacedDigits(text: String): String? {
        val singleDigitMatches = Regex("\\b\\d\\b").findAll(text).map { it.value }.toList()
        if (singleDigitMatches.size >= 2) {
            return singleDigitMatches.joinToString("")
        }
        return null
    }

    private fun parseEnglishTokens(tokens: List<String>): String? {
        // Compound check: e.g. "sixty three" -> 60 + 3 = 63 (with fuzzy tolerance)
        for (i in 0 until tokens.size - 1) {
            val tensVal = matchEnglishTens(tokens[i])
            val unitsVal = matchEnglishUnits(tokens[i + 1])
            if (tensVal != null && unitsVal != null && unitsVal < 10) {
                return (tensVal + unitsVal).toString()
            }
        }

        // Single token tens or units (e.g. "sixty" -> 60, "seventy" -> 70, "twelve" -> 12)
        for (token in tokens) {
            val tens = matchEnglishTens(token)
            if (tens != null) return tens.toString()
            val unit = matchEnglishUnits(token)
            if (unit != null && unit >= 10) return unit.toString()
        }

        // Digit-by-digit spoken numbers (e.g. "six three" -> "63", "seven four" -> "74")
        val digitSequence = StringBuilder()
        for (token in tokens) {
            val d = matchEnglishUnits(token)
            if (d != null && d < 10) {
                digitSequence.append(d)
            } else if (digitSequence.length >= 2) {
                break
            } else {
                digitSequence.clear()
            }
        }
        if (digitSequence.length >= 2) {
            return digitSequence.toString()
        }

        return null
    }

    private fun matchEnglishTens(token: String): Int? {
        englishTens[token]?.let { return it }
        // Fuzzy match for tens (e.g. "sixti" -> "sixty", "seventi" -> "seventy")
        for ((word, value) in englishTens) {
            val maxAllowedDist = if (word.length <= 5) 1 else 2
            if (levenshteinDistance(token, word) <= maxAllowedDist) {
                return value
            }
        }
        return null
    }

    private fun matchEnglishUnits(token: String): Int? {
        englishUnits[token]?.let { return it }
        // Fuzzy match for units (e.g. "tree" -> "three", "for" -> "four", "ate" -> "eight")
        for ((word, value) in englishUnits) {
            if (word.length >= 4 && levenshteinDistance(token, word) <= 1) {
                return value
            }
        }
        return null
    }

    /**
     * Requirement 6: Fuzzy / Levenshtein distance matching against Hindi & Hinglish dictionaries
     */
    private fun fuzzyMatchToken(tokens: List<String>): String? {
        // Check single tokens against Hinglish dictionary
        for (token in tokens) {
            if (token.length < 3) continue

            var bestMatch: Int? = null
            var minDistance = Int.MAX_VALUE

            for ((dictWord, num) in hinglishPhoneticMap) {
                val maxAllowedDist = when {
                    dictWord.length <= 4 -> 1
                    dictWord.length <= 7 -> 2
                    else -> 2
                }
                val dist = levenshteinDistance(token, dictWord)
                if (dist <= maxAllowedDist && dist < minDistance) {
                    minDistance = dist
                    bestMatch = num
                }
            }

            if (bestMatch != null && minDistance <= 2) {
                return bestMatch.toString()
            }
        }

        // Check tokens against Hindi script dictionary
        for (token in tokens) {
            if (token.length < 3) continue

            var bestMatch: Int? = null
            var minDistance = Int.MAX_VALUE

            for ((dictWord, num) in hindiScriptMap) {
                val maxAllowedDist = when {
                    dictWord.length <= 3 -> 1
                    dictWord.length <= 6 -> 2
                    else -> 2
                }
                val dist = levenshteinDistance(token, dictWord)
                if (dist <= maxAllowedDist && dist < minDistance) {
                    minDistance = dist
                    bestMatch = num
                }
            }

            if (bestMatch != null && minDistance <= 2) {
                return bestMatch.toString()
            }
        }

        return null
    }
}
