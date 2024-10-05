package mar

fun <K,V> List<Map<K,V>>.union (): Map<K,V> {
    return this.fold(emptyMap()) { acc, value -> acc + value }
}

fun <V> Stmt.dn_collect (f: (Stmt)->List<V>?): List<V> {
    val v = f(this)
    if (v === null) {
        return emptyList()
    }
    return v + when (this) {
        is Stmt.Proto  -> this.blk.dn_collect(f) + this.pars.map { it.dn_collect(f) }.flatten()
        is Stmt.Do     -> this.es.map { it.dn_collect(f) }.flatten()
        is Stmt.Group  -> this.es.map { it.dn_collect(f) }.flatten()
        is Stmt.Enclose -> this.es.map { it.dn_collect(f) }.flatten()
        is Stmt.Escape -> this.e?.dn_collect(f) ?: emptyList()
        is Stmt.Dcl    -> this.src?.dn_collect(f) ?: emptyList()
        is Stmt.Set    -> this.dst.dn_collect(f) + this.src.dn_collect(f)
        is Stmt.If     -> this.cnd.dn_collect(f) + this.t.dn_collect(f) + this.f.dn_collect(f)
        is Stmt.Loop   -> this.blk.dn_collect(f)
        is Stmt.Drop   -> this.e.dn_collect(f)

        is Stmt.Catch  -> this.blk.dn_collect(f)
        is Stmt.Defer  -> this.blk.dn_collect(f)

        is Stmt.Yield  -> this.e.dn_collect(f)
        is Stmt.Resume -> this.co.dn_collect(f) + this.args.map { it.dn_collect(f) }.flatten()

        is Stmt.Spawn  -> (this.tsks?.dn_collect(f) ?: emptyList()) + this.tsk.dn_collect(f) + this.args.map { it.dn_collect(f) }.flatten()
        is Stmt.Delay  -> emptyList()
        is Stmt.Pub    -> this.tsk?.dn_collect(f) ?: emptyList()
        is Stmt.Toggle -> this.tsk.dn_collect(f) + this.on.dn_collect(f)
        is Stmt.Tasks  -> this.max.dn_collect(f)

        is Stmt.Tuple  -> this.args.map { it.dn_collect(f) }.flatten()
        is Stmt.Vector -> this.args.map { it.dn_collect(f) }.flatten()
        is Stmt.Dict   -> this.args.map { it.first.dn_collect(f) + it.second.dn_collect(f) }.flatten()
        is Stmt.Index  -> this.col.dn_collect(f) + this.idx.dn_collect(f)
        is Stmt.Call   -> this.clo.dn_collect(f) + this.args.map { it.dn_collect(f) }.flatten()

        is Stmt.Acc, is Stmt.Data, is Stmt.Nat,
        is Stmt.Nil, is Stmt.Tag, is Stmt.Bool,
        is Stmt.Char, is Stmt.Num -> emptyList()
    }
}

fun Stmt.dn_visit (f: (Stmt)->Unit) {
    this.dn_collect { f(it) ; emptyList<Unit>() }
}

fun trap (f: ()->Unit): String {
    try {
        f()
        error("impossible case")
    } catch (e: Throwable) {
        return e.message!!
    }
}

fun Pos.is_same_line (oth: Pos): Boolean {
    return (this.file==oth.file && this.lin==oth.lin && this.brks==oth.brks)
}

fun <T> T?.cond2 (f: (v:T)->String, g: (()->String)?): String {
    return when (this) {
        false, null -> if (g !== null) g() else ""
        else  -> f(this)
    }
}

fun <T> T?.cond (f: (v:T)->String): String {
    return this.cond2(f) {""}
}
fun String.quote (n: Int): String {
    return this
        .replace('\n',' ')
        .replace('"','.')
        .replace('\\','.')
        .let {
            if (it.length<=n) it else it.take(n-3)+"..."
        }

}

fun err (pos: Pos, str: String): Nothing {
    error(pos.file + " : (lin ${pos.lin}, col ${pos.col}) : $str")
}
fun err (tk: Tk, str: String): Nothing {
    err(tk.pos, str)
}
fun err_expected (tk: Tk, str: String) {
    val have = when {
        (tk is Tk.Eof) -> "end of file"
        else -> '"' + tk.str + '"'
    }
    err(tk, "expected $str : have $have")
}

fun Array<String>.cmds_opts () : Pair<List<String>,Map<String,String?>> {
    val cmds = this.filter { !it.startsWith("--") }
    val opts = this
        .filter { it.startsWith("--") }
        .map {
            if (it.contains('=')) {
                val (k,v) = Regex("(--.*)=(.*)").find(it)!!.destructured
                Pair(k,v)
            } else {
                Pair(it, null)
            }
        }
        .toMap()
    return Pair(cmds,opts)
}

fun String.idc (): String {
    return when {
        (this[0] == '{') -> {
            val MAP = mapOf(
                Pair('+', "plus"),
                Pair('-', "minus"),
                Pair('*', "asterisk"),
                Pair('/', "slash"),
                Pair('>', "greater"),
                Pair('<', "less"),
                Pair('=', "equals"),
                Pair('!', "exclaim"),
                Pair('|', "bar"),
                Pair('&', "ampersand"),
                Pair('#', "hash"),
            )
            this.drop(2).dropLast(2).toList().map { MAP[it] }.joinToString("_")
        }
        else -> {
            val MAP = mapOf(
                Pair(':', ""),
                Pair('.', "_"),
                Pair('-', "_dash_"),
                Pair('\'', "_plic_"),
                Pair('?', "_question_"),
                Pair('!', "_bang_"),
            )
            this.toList().map { MAP[it] ?: it }.joinToString("")
        }
    }
}
