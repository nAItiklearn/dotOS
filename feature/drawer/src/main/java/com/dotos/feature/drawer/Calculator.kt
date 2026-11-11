package com.dotos.feature.drawer

/** Tiny local expression evaluator for the search bar: + - * / % ^ and parentheses. */
internal object Calculator {
    fun evaluate(input: String): String? {
        val s = input.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
        if (s.length < 3 || s.none { it in "+-*/^%" } || s.any { !(it.isDigit() || it in "+-*/^%().") }) return null
        return runCatching {
            val p = Parser(s)
            val v = p.expr()
            if (p.pos != s.length || v.isNaN() || v.isInfinite()) return null
            format(v)
        }.getOrNull()
    }

    private fun format(v: Double): String {
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        return "%.8f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun expr(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = power()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/' || s[pos] == '%')) {
                val op = s[pos++]
                val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        fun power(): Double {
            val base = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                return Math.pow(base, power())
            }
            return base
        }

        fun unary(): Double {
            if (pos < s.length && s[pos] == '-') {
                pos++
                return -unary()
            }
            return atom()
        }

        fun atom(): Double {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = expr()
                require(pos < s.length && s[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}
