import sparql.tests.compareIncrementalSelectOutput

suspend fun main() {
    compareIncrementalSelectOutput().run(count = 5).report()
//    compareIncrementalBasicGraphPatternOutput().run().report()
}
