import sparql.tests.compareIncrementalBasicGraphPatternOutput


suspend fun main() {
    compareIncrementalBasicGraphPatternOutput().run().report()
}
