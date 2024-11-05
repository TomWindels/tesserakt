import sparql.tests.compareIncrementalSelectOutput

suspend fun main() {
    compareIncrementalSelectOutput().run(count = 1).report()
//    compareIncrementalBasicGraphPatternOutput().run().report()
}
