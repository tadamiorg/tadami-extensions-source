package com.sf.tadami.lib.unpacker

/*
 * Copyright (C) The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * Helper class to unpack JavaScript code compressed by [packer](http://dean.edwards.name/packer/).
 *
 * Source code of packer can be found [here](https://github.com/evanw/packer/blob/master/packer.js).
 */
object Unpacker {

    /**
     * Unpacks JavaScript code compressed by packer.
     *
     * Specify [left] and [right] to unpack only the data between them.
     *
     * Note: single quotes `\'` in the data will be replaced with double quotes `"`.
     */
    fun unpack(script: String, left: String? = null, right: String? = null): String =
        unpack(SubstringExtractor(script), left, right)

    /**
     * Unpacks JavaScript code compressed by packer.
     *
     * Specify [left] and [right] to unpack only the data between them.
     *
     * Note: single quotes `\'` in the data will be replaced with double quotes `"`.
     */
    fun unpack(script: SubstringExtractor, left: String? = null, right: String? = null): String {
        // Lenient tail: matches both the standard "}('…'.split('|'),0,{}))" and
        // simplified packer variants ending in "}('…'.split('|')))".
        val packed = script
            .substringBetween("}('", ".split('|')")
            .replace("\\'", "\"")

        val parser = SubstringExtractor(packed)
        val data: String
        if (left != null && right != null) {
            data = parser.substringBetween(left, right)
            parser.skipOver("',")
        } else {
            data = parser.substringBefore("',")
        }
        if (data.isEmpty()) return ""

        val radix = parser.substringBefore(",").trim().toIntOrNull() ?: 62

        val dictionary = parser.substringBetween("'", "'").split("|")
        val size = dictionary.size

        return wordRegex.replace(data) {
            val key = it.value
            val index = parseRadix(key, radix) ?: return@replace key
            if (index >= size) return@replace key
            dictionary[index].ifEmpty { key }
        }
    }

    private val wordRegex by lazy { Regex("""\w+""") }

    private fun parseRadix(str: String, radix: Int): Int? {
        if (radix >= 37) return parseRadix62(str)
        // Packers with radix <= 36 encode tokens with c.toString(radix), which is
        // lowercase and matched case-sensitively; uppercase words are never tokens.
        if (str.any { it !in '0'..'9' && it !in 'a'..'z' }) return null
        return str.toIntOrNull(radix)
    }

    private fun parseRadix62(str: String): Int {
        var result = 0
        for (ch in str.toCharArray()) {
            result = result * 62 + when {
                ch.code <= '9'.code -> { // 0-9
                    ch.code - '0'.code
                }

                ch.code >= 'a'.code -> { // a-z
                    // ch - 'a' + 10
                    ch.code - ('a'.code - 10)
                }

                else -> { // A-Z
                    // ch - 'A' + 36
                    ch.code - ('A'.code - 36)
                }
            }
        }
        return result
    }
}
