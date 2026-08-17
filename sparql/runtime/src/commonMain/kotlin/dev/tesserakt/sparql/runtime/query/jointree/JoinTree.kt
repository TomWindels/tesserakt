package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.query.MutableJoinState

/**
 * A general join tree type, containing intermediate joined values depending on the tree implementation
 */
interface JoinTree : MutableJoinState {

    companion object

}
