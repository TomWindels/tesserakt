package core.sparql.query

data class PatternBlock(
    val patterns: List<Segment>
) {

    // TODO: actually use this class for a set of patterns sharing the subject
    class Segment {

    }

    // TODO: build method that actually optimises the incoming patterns, w/ limited ctor visibility
    // TODO: using `patterns` to do the actual fetching from `Store`, keeping a bitmask per incoming subject
    //  of matched patterns

}