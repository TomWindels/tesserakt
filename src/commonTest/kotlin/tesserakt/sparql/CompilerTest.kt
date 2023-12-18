package tesserakt.sparql

import tesserakt.TestEnvironment.Companion.test
import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Triple
import tesserakt.rdf.types.Triple.Companion.asNamedTerm
import tesserakt.sparql.compiler.CompilerError
import tesserakt.sparql.compiler.analyser.AggregatorProcessor
import tesserakt.sparql.compiler.processed
import tesserakt.sparql.compiler.types.Aggregation
import tesserakt.sparql.compiler.types.Aggregation.Companion.builtin
import tesserakt.sparql.compiler.types.Aggregation.Companion.distinctBindings
import tesserakt.sparql.compiler.types.PatternAST
import tesserakt.sparql.compiler.types.SelectQueryAST
import kotlin.test.Test

class CompilerTest {

    @Test
    fun select() = test {
        /* content tests */
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o ; }" satisfies {
            val pattern = PatternAST(
                s = PatternAST.Binding("s"),
                p = PatternAST.Binding("p"),
                o = PatternAST.Binding("o")
            )
            body.patterns.size == 1 && body.patterns.first() == pattern
        }
        "select*{?s?p?o}" satisfies {
            body.patterns.size == 1
        }
        "prefix ex: <http://example.org/> select*{?s ex:prop ?o}" satisfies {
            body.patterns.size == 1
        }
        "prefix select: <http://example.org/> select*{?s select:prop ?o ; <prop> select:test}" satisfies {
            body.patterns.size == 2
                && body.patterns[0].p == PatternAST.Exact(Triple.NamedTerm("http://example.org/prop"))
                && body.patterns[1].o == PatternAST.Exact(Triple.NamedTerm("http://example.org/test"))
        }
        "SELECT * WHERE { ?s a/<predicate2>*/<predicate3>?o. }" satisfies {
            body.patterns.first().p is PatternAST.Chain
        }
        "SELECT * WHERE { ?s a/?p1*/?p2?o. }" satisfies {
            require(this is SelectQueryAST)
            body.patterns.first().p is PatternAST.Chain
                && output.names == setOf("s", "p1", "p2", "o")
                && output.entries.all { it.value is SelectQueryAST.Output.BindingEntry }
        }
        "SELECT * WHERE { ?s (<predicate2>|<predicate3>)?o. }" satisfies {
            body.patterns.first().p is PatternAST.Alts
        }
        "SELECT * WHERE { ?s <contains>/(<prop1>|!<prop2>)* ?o2 }" satisfies {
            body.patterns.first().p.let { p -> p is PatternAST.Chain && p.chain[1] is PatternAST.ZeroOrMore }
        }
        "SELECT ?s?p?o WHERE {?s?p?o2;?p2?o.}" satisfies {
            body.patterns.size == 2 && body.patterns[1].p == PatternAST.Binding("p2")
        }
        "SELECT ?s WHERE {?s<prop><value>}" satisfies {
            val pattern = PatternAST(
                s = PatternAST.Binding("s"),
                p = PatternAST.Exact(Triple.NamedTerm("prop")),
                o = PatternAST.Exact(Triple.NamedTerm("value"))
            )
            body.patterns.size == 1 && body.patterns.first() == pattern
        }
        "SELECT ?s WHERE{{?s<prop><value>}UNION{?s<prop2><value2>}UNION{?s<prop3><value3>}}" satisfies {
            body.unions.size == 1 && body.unions.first().size == 3
        }
        """
            SELECT * WHERE {
                ?s a <type>
                {
                    ?s <prop> <value> 
                } UNION {
                    ?s <prop2> <value2>
                } UNION {
                    ?s <prop3> <value3>
                }
            }
        """ satisfies {
            body.patterns.size == 1 && body.unions.size == 1 && body.unions.first().size == 3
        }
        """
            SELECT * WHERE {
                ?s a <type>
                OPTIONAL { ?s <has> ?content }
                { ?s <prop> ?value1 } UNION { ?s <prop2> <value2> } UNION { ?s <prop3> <value3> }
            }
        """ satisfies {
            require(this is SelectQueryAST)
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            body.unions.size == 1 &&
            output.names == setOf("s", "content", "value1")
        }
        """
            SELECT * WHERE {
                ?s <name> ?name .
                # see: https://jena.apache.org/tutorials/sparql_filters.html
                FILTER regex(?name, "test", "i")
            }
        """ satisfies {
            // TODO: check filter
            true
        }
        """
            SELECT * WHERE {
                ?s <has> ?value .
                # see: https://jena.apache.org/tutorials/sparql_filters.html
                FILTER(?value > 5)
            }
        """ satisfies {
            // TODO: check filter
            true
        }
        """
            SELECT * WHERE {
                ?s a <type>
                # see: https://jena.apache.org/tutorials/sparql_optionals.html
                OPTIONAL { ?s <has> ?value . FILTER(?value > 5) }
            }
        """ satisfies {
            require(this is SelectQueryAST)
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            output.names == setOf("s", "value")
            // TODO: also check the optional's condition
        }
        "select(count(distinct ?s) as ?count){?s?p?o}" satisfies {
            require(this is SelectQueryAST)
            val func = output.aggregate("count")!!.root.builtin
            func.type == Aggregation.Builtin.Type.COUNT &&
            func.input.distinctBindings == Aggregation.DistinctBindingValues("s")
        }
        "select(avg(?s) + min(?s) / 3 as ?count){?s?p?o}" satisfies {
            require(this is SelectQueryAST)
            output.aggregate("count")!!.root ==
                    "(${1 / 3.0} * min(?s)) + avg(?s)".processed(AggregatorProcessor()).getOrThrow()
        }
        "select(avg(?s) + min(?s)/3*4+3-5.5*10 as ?count_long){?s?p?o}" satisfies {
            require(this is SelectQueryAST)
            output.aggregate("count_long")!!.root ==
                    "4 * (min(?s)) / 3 + avg(?s) - 52".processed(AggregatorProcessor()).getOrThrow()
        }
        "select(-min(?s) + max(?s) as ?reversed_diff){?s?p?o}" satisfies {
            require(this is SelectQueryAST)
            output.aggregate("reversed_diff")!!.root ==
                    "max(?s) - min(?s)".processed(AggregatorProcessor()).getOrThrow()
        }
        """
            PREFIX : <http://example.com/data/#>
            SELECT ?g (AVG(?p) AS ?avg) ((MIN(?p) + MAX(?p)) / 2 AS ?c)
            WHERE {
              ?g :p ?p .
            }
            GROUP BY ?g
        """ satisfies {
            require(this is SelectQueryAST)
            val pattern = PatternAST(
                s = PatternAST.Binding("g"),
                p = PatternAST.Exact(Triple.NamedTerm("http://example.com/data/#p")),
                o = PatternAST.Binding("p")
            )
            body.patterns.size == 1 &&
            body.patterns.first() == pattern &&
            output.aggregate("avg")!!.root.builtin.type == Aggregation.Builtin.Type.AVG &&
            output.aggregate("c")!!.root == ".5 * (max(?p) + min(?p))".processed(AggregatorProcessor()).getOrThrow()
        }
        """
            SELECT * WHERE {
                ?s ?p [ a <type> ; ]
            }
        """ satisfies {
            val blankPatterns = body.patterns.firstOrNull()?.o as? PatternAST.BlankObject ?: return@satisfies false

            body.patterns.size == 1 &&
            blankPatterns.properties.size == 1 &&
            blankPatterns.properties.first() == PatternAST.BlankObject.BlankPattern(
                p = PatternAST.Exact(RDF.type),
                o = PatternAST.Exact("type".asNamedTerm())
            )
        }
        """
            SELECT * WHERE {
                ?s ?p [ <contains> [ <data> ?values ] ; ]
            }
        """ satisfies {
            require(this is SelectQueryAST)
            val first = body.patterns.firstOrNull()?.o as? PatternAST.BlankObject ?: return@satisfies false
            val second = first.properties.firstOrNull()?.o as? PatternAST.BlankObject ?: return@satisfies false

            body.patterns.size == 1 &&
            first.properties.size == 1 &&
            second.properties.size == 1 &&
            second.properties.first() == PatternAST.BlankObject.BlankPattern(
                p = PatternAST.Exact("data".asNamedTerm()),
                o = PatternAST.Binding("values")
            ) &&
            "values" in output.names
        }
        """
            SELECT * WHERE {
                ?s ?p [
                    a <type> ;
                    <contains> ?data1, ?data2, ?data3
                ], [
                    a <other-type> ;
                ]
            }
        """ satisfies {
            val first = body.patterns.getOrNull(0)?.o as? PatternAST.BlankObject ?: return@satisfies false
            val second = body.patterns.getOrNull(1)?.o as? PatternAST.BlankObject ?: return@satisfies false

            body.patterns.size == 2 &&
            first.properties.size == 4 &&
            second.properties.size == 1 &&
            second.properties.first() == PatternAST.BlankObject.BlankPattern(
                p = PatternAST.Exact(RDF.type),
                o = PatternAST.Exact("other-type".asNamedTerm())
            )
        }
        """
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX foaf: <http://xmlns.com/foaf/0.1/>

            SELECT * WHERE
            { 
                {
                    SELECT ?page ("A" AS ?type) WHERE 
                    {
                         ?s rdfs:label "Microsoft"@en;
                            foaf:page ?page
                    }
                }
                UNION
                {
                    SELECT ?page ("B" AS ?type) WHERE 
                    {
                         ?s rdfs:label "Apple"@en;
                            foaf:page ?page
                    }
                }
            }
        """ satisfies {
            require(this is SelectQueryAST)
            output.names.containsAll(listOf("page, type"))
        }
        /* expected failure cases */
        "SELECT TEST WHERE { ?s a TEST . }" causes CompilerError.Type.SyntaxError
        "SELECT * WHERE { ?s a ?test " causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s <predicate2>/(<predicate3> ?o2.}" causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s a ?type , }" causes CompilerError.Type.StructuralError
        "PREFIX ex: <http://example.org> SELECT * WHERE { ?s ex:prop/other ?o }" causes CompilerError.Type.SyntaxError
        "prefix ex: <http://example.org> select*{?s dc:title ?o}" causes CompilerError.Type.StructuralError
        "select(count(distinct ?s as ?count){?s?p?o}" causes CompilerError.Type.StructuralError
    }

}
