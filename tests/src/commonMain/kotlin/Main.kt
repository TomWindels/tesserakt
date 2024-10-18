import sparql.tests.compareIncrementalSelectOutput

suspend fun main() {
    compareIncrementalSelectOutput().run(count = 2).report()
//    compareIncrementalBasicGraphPatternOutput().run().report()
}
