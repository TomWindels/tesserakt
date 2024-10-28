external val process: dynamic

suspend fun main(args: Array<String>) {
    println(process.cwd())
    run((process.argv as Array<String>).drop(2).toTypedArray())
}
