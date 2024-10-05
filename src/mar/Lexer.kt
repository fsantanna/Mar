package mar

import java.io.File
import java.io.PushbackReader
import java.io.Reader
import java.io.StringReader

data class Lex (
    var file: String,
    var lin: Int, var col: Int, var brks: Int,
    var prv: Int, // previous col before \n to restore on unread
    val reader: PushbackReader
)
data class Pos (val file: String, val lin: Int, val col: Int, val brks: Int)

fun Lex.toPos (): Pos {
    return Pos(this.file, this.lin, this.col, this.brks)
}

fun FileX (path: String): File {
    val xpath = if (path[0] != '@') path else {
        PATH + "/" + path.drop(2)
    }
    return File(xpath)
}

// TODO: reads 65535 after unreading -1
fun iseof (n: Int): Boolean {
    return (n==-1 || n==65535)
}

typealias Lexer = List<Pair<Triple<String,Int,Int>,Reader>>

fun String.lexer (): Sequence<Tk> {
    val lex: Lexer = listOf(Pair(Triple("anon",1,1), this.reader()))
    return lex.lexer()
}

fun Lexer.lexer (): Sequence<Tk> = sequence {
    val stack = ArrayDeque<Lex>()
    val comms = ArrayDeque<String>()

    for (inp in this@lexer) {
        stack.addFirst(Lex(inp.first.first, inp.first.second, inp.first.third, 0, 0, PushbackReader(inp.second,2)))
    }

    fun read2 (): Pair<Int,Char> {
        val pos = stack.first()
        val n = pos.reader.read()
        val x = n.toChar()
        pos.prv = pos.col
        when {
            (x == '\n') -> { pos.lin++; pos.col=1}
            (x == ';')  -> { pos.col++ ; pos.brks++ }
            !iseof(n)   -> pos.col++
        }
        return Pair(n,x)
    }
    fun unread2 (n: Int) {
        val pos = stack.first()
        val x = n.toChar()
        pos.reader.unread(n)
        when {
            iseof(n) -> {}
            (x == ';') -> pos.brks--
            (x == '\n') -> { pos.lin--; pos.col=pos.prv }
            else -> pos.col = pos.col-1
        }
    }

    fun read2Until (f: (x: Char)->Boolean): String? {
        var ret = ""
        while (true) {
            val (n,x) = read2()
            when {
                iseof(n) -> {
                    unread2(n)
                    return null
                }
                f(x) -> break
                else -> ret += x
            }
        }
        return ret
    }
    fun read2Until (x: Char): String? {
        return read2Until { it == x }
    }
    fun read2While (f: (x: Char)->Boolean): String {
        var ret = ""
        while (true) {
            val (n,x) = read2()
            when {
                f(x) -> ret += x
                else -> {
                    unread2(n)
                    break
                }
            }
        }
        return ret
    }
    fun read2While (x: Char): String {
        return read2While { it == x }
    }

    fun next (): Pair<Char?, Pos> {
        while (true) {
            val lex = stack.first()
            val pos = lex.toPos()
            val (n1,x1) = read2()
            when (x1) {
                ' ', '\t', '\n' -> {}
                ';' -> {
                    val (n2,x2) = read2()
                    if (x2 == ';') {
                        val x3 = ";;" + read2While(';')
                        if (x3 == ";;") {
                            read2Until('\n')
                        } else {
                            var x4 = x3
                            outer@ while (true) {
                                if (comms.firstOrNull() == x4) {
                                    comms.removeFirst()
                                    if (comms.size == 0) {
                                        break
                                    }
                                } else {
                                    comms.addFirst(x4)
                                }
                                do {
                                    if (read2Until(';') == null) {
                                        break@outer
                                    }
                                    x4 = ";" + read2While(';')
                                } while (x4.length<=2 || x4.length<comms.first().length)
                            }
                        }
                    } else {
                        unread2(n2)
                    }
                }
                else -> {
                    return if (iseof(n1)) {
                        Pair(null, pos)
                    } else {
                        Pair(x1, pos)
                    }
                }
            }
        }
    }

    while (true) {
        val (x,pos) = next()
        when {
            (x == null) -> {
                if (stack.size > 1) {
                    stack.removeFirst()
                } else {
                    yield(Tk.Eof(pos))
                    break
                }
            }
            (x in listOf('{','}','(',')','[',']',',','\$','.',':')) -> yield(Tk.Fix(x.toString(), pos))
            (x in OPERATORS.first) -> {
                val op = x + read2While { it in OPERATORS.first }
                when {
                    (op == "=") -> yield(Tk.Fix(op, pos))
                    (op in OPERATORS.second) -> yield(Tk.Op(op, pos))
                    else -> err(pos, "token error : unexpected $x")
                }
            }
            (x.isLetter() || x=='_') -> {
                val id = x + read2While { a -> a.isLetterOrDigit() || a=='_' }
                when {
                    KEYWORDS.contains(id) -> yield(Tk.Fix(id, pos))
                    x.isUpperCase() -> yield(Tk.Type(id, pos))
                    else -> yield(Tk.Var(id, pos))
                }
            }
            x.isDigit() -> {
                val num = x + read2While { it=='.' || it.isLetterOrDigit() }
                yield(Tk.Num(num, pos))
            }
            (x == '`') -> {
                val open = x + read2While('`')
                var nat = ""
                val tag = read2().let { (n2,x2) ->
                    if (x2 != ':') {
                        unread2(n2)
                        null
                    } else {
                        val tag = x2 + read2While { it.isLetterOrDigit() || it=='.' }
                        if (tag.length < 2) {
                            err(pos, "tag error : expected identifier")
                        }
                        tag
                    }
                }
                while (true) {
                    val (n2,x2) = read2()
                    when {
                        iseof(n2) -> {
                            err(stack.first().toPos(), "native error : expected \"$open\"")
                        }
                        (x2 == '`') -> {
                            val xxx = stack.first().toPos().let { Pos(it.file,it.lin,it.col-1,it.brks) }
                            val close = x2 + read2While('`')
                            if (open == close) {
                                break
                            } else {
                                err(xxx, "native error : expected \"$open\"")
                            }
                        }
                    }
                    nat += x2
                }
                //println(":$pay:")
                yield(Tk.Nat(nat, pos, tag))
            }
            (x == '^') -> {
                val (_,x2) = read2()
                if (x2 != '[') {
                    err(pos, "token ^ error : expected \"^[\"")
                }
                val (n3, x3) = read2()
                val file: String? = if (x3 == '"') {
                    val f = read2Until('"')
                    if (f == null) {
                        err(pos, "token ^ error : unterminated \"")
                    }
                    f
                } else {
                    unread2(n3)
                    null
                }

                val lin: Int? = if (file == null) {
                    read2While { it.isDigit() }.toIntOrNull()
                } else {
                    val (n4, x4) = read2()
                    if (x4 == ',') {
                        read2While { it.isDigit() }.let {
                            if (it.isEmpty()) {
                                err(pos, "token ^ error : expected number")
                            }
                            it.toInt()
                        }
                    } else {
                        unread2(n4)
                        null
                    }
                }
                val col: Int? = if (lin == null) {
                    null
                } else {
                    val (n5, x5) = read2()
                    if (x5 == ',') {
                        read2While { it.isDigit() }.let {
                            if (it.isEmpty()) {
                                err(pos, "token ^ error : expected number")
                            }
                            it.toInt()
                        }
                    } else {
                        unread2(n5)
                        null
                    }
                }

                if (file == null && lin == null) {
                    err(pos, "token ^ error")
                }

                val (_, x6) = read2()
                if (x6 != ']') {
                    err(pos, "token ^ error : expected \"]\"")
                }
                val (n7, x7) = read2()
                when {
                    iseof(n7) -> unread2(n7)
                    (x7 == '\n') -> {}  // skip leading \n
                    else -> unread2(n7) //err(pos, "token ^ error : expected end of line")
                }

                when {
                    (file != null && lin == null && col == null) -> {
                        val f = FileX(file)
                        if (!f.exists()) {
                            err(pos, "token ^ error : file not found : $file")
                        }
                        stack.addFirst(Lex(file, 1, 1, 0, 0, PushbackReader(StringReader(f.readText()), 2)))
                    }

                    (lin != null) -> stack.first().let {
                        it.file = if (file == null) it.file else file
                        it.lin = lin
                        it.col = if (col == null) 1 else col
                    }
                    else -> error("bug found")
                }
            }
            (x == '\'') -> {
                val (n2,x2) = read2()
                if (iseof(n2)) {
                    err(stack.first().toPos(), "char error : expected '")
                }
                val c = if (x2 != '\\') x2.toString() else {
                    val (n3,x3) = read2()
                    if (iseof(n3)) {
                        err(stack.first().toPos(), "char error : expected '")
                    }
                    x2.toString()+x3
                }
                val (n3,x3) = read2()
                if (iseof(n3) || x3!='\'') {
                    err(stack.first().toPos(), "char error : expected '")
                }
                yield(Tk.Chr("'$c'", pos))
            }
            (x == '"') -> {
                var n = 0
                val v = read2Until {
                    val brk = (it=='"' && n%2==0)
                    n = if (it == '\\') n+1 else 0
                    brk
                }
                if (v == null) {
                    err(pos, "string error : unterminated \"")
                }
                yield(Tk.Fix("#[", pos))
                var i = 0
                while (i < v.length) {
                    if (i > 0) {
                        yield(Tk.Fix(",", pos))
                    }
                    val z = v[i]
                    val zz = when {
                        (z == '\'') -> "\\'"
                        (z != '\\') -> z.toString()
                        else -> {
                            i++
                            z.toString() + v[i]
                        }
                    }
                    yield(Tk.Chr("'$zz'", pos))
                    i++
                }
                yield(Tk.Fix("]", pos))
            }
            else -> {
                err(pos, "token error : unexpected $x")
            }
        }
    }
}
