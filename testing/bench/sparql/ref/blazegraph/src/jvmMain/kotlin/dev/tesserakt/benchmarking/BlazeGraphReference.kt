package dev.tesserakt.benchmarking

import com.bigdata.rdf.sail.BigdataSail
import com.bigdata.rdf.sail.BigdataSailRepository
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.SnapshotStore
import org.openrdf.model.*
import org.openrdf.model.impl.BNodeImpl
import org.openrdf.model.impl.LiteralImpl
import org.openrdf.model.impl.StatementImpl
import org.openrdf.model.impl.URIImpl
import org.openrdf.query.BindingSet
import org.openrdf.query.QueryLanguage
import java.util.*


class BlazeGraphReference(private val query: String) : Reference() {

    private var previous = emptyList<BindingSet>()
    private var current = emptyList<BindingSet>()

    private var checksum = 0

    override fun prepare(diff: SnapshotStore.Diff) {
        val conn = repo.connection
        conn.begin()
        try {
            conn.remove(diff.deletions.map { it.toStatement() })
            conn.add(diff.insertions.map { it.toStatement() })
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.close()
        }
    }

    override suspend fun eval() {
        val conn = repo.readOnlyConnection
        val eval = try {
            conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
        } catch (e: Exception) {
            throw e
        } finally {
            conn.close()
        }
        val results = mutableListOf<BindingSet>()
        try {
            while (eval.hasNext()) {
                val next = eval.next()
                results.add(next)
                next.forEach { binding ->
                    checksum += binding.value.checksumValue
                }
            }
        } finally {
            eval.close()
        }
        current = results
    }

    override fun finish(): Output {
        val result = compare(current, previous, checksum = checksum)
        checksum = 0
        previous = current
        return result
    }

    override fun close() {
        val conn = repo.connection
        conn.begin()
        conn.clear()
        conn.commit()
        conn.close()
    }

}

// reusing the repo setup, as it doesn't shut down properly (and thus reinitializing is not possible)
private val repo by lazy {
    BigdataSailRepository(BigdataSail(properties))
        .apply { initialize() }
}

private val properties = Properties().apply {
    // # changing the axiom model to none essentially disables all inference
    // com.bigdata.rdf.store.AbstractTripleStore.axiomsClass=com.bigdata.rdf.axioms.NoAxioms
    set("com.bigdata.rdf.store.AbstractTripleStore.axiomsClass", "com.bigdata.rdf.axioms.NoAxioms")
    // # RWStore (scalable single machine backend)
    // com.bigdata.journal.AbstractJournal.bufferMode=DiskRW
    set("com.bigdata.journal.AbstractJournal.bufferMode", "DiskRW")
    set("com.bigdata.journal.AbstractJournal.file", "/tmp/blazegraph/test.jnl")
    // # turn off automatic inference in the SAIL
    // com.bigdata.rdf.sail.truthMaintenance=false
    set("com.bigdata.rdf.sail.truthMaintenance", "false")
    // # don't store justification chains, meaning retraction requires full manual
    // # re-closure of the database
    // com.bigdata.rdf.store.AbstractTripleStore.justify=false
    set("com.bigdata.rdf.store.AbstractTripleStore.justify", "false")
    // # turn off the statement identifiers feature for provenance
    // com.bigdata.rdf.store.AbstractTripleStore.statementIdentifiers=false
    set("com.bigdata.rdf.store.AbstractTripleStore.statementIdentifiers", "false")
    // # turn off the free text index
    // com.bigdata.rdf.store.AbstractTripleStore.textIndex=false
    set("com.bigdata.rdf.store.AbstractTripleStore.textIndex", "false")
}

private fun Quad.toStatement(): StatementImpl {
    return StatementImpl(
        s.toStatementSubject(),
        p.toStatementPredicate(),
        o.toStatementObject(),
    )
}

private fun Quad.Term.toStatementSubject(): Resource = when (this) {
    is Quad.BlankTerm -> toBNode()
    is Quad.Literal -> throw IllegalArgumentException("`${this}` is not a valid subject!")
    is Quad.NamedTerm -> toURI()
}

private fun Quad.Term.toStatementPredicate(): URIImpl = when (this) {
    is Quad.NamedTerm -> toURI()

    is Quad.Literal,
    is Quad.BlankTerm -> throw IllegalArgumentException("`${this}` is not a valid predicate!")
}

private fun Quad.Term.toStatementObject(): Value = when (this) {
    is Quad.BlankTerm -> toBNode()
    is Quad.Literal -> LiteralImpl(value, type.toURI())
    is Quad.NamedTerm -> toURI()
}

private fun Quad.NamedTerm.toURI(): URIImpl = URIImpl(value)

private fun Quad.BlankTerm.toBNode(): BNodeImpl {
    return BNodeImpl(id.toString())
}

private val Value.checksumValue: Int
    get() = when (this) {
        is BNode -> id.count { it.isDigit() }
        is URI -> stringValue().length
        is Literal -> stringValue().length
        else -> throw IllegalArgumentException("Unknown value type ${this::class.simpleName}")
    }
