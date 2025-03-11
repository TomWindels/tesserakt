
import TestEnvironment.Companion.test
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.compiler.CompilerError
import dev.tesserakt.sparql.types.ast.ExpressionAST
import dev.tesserakt.sparql.types.ast.PatternAST
import dev.tesserakt.sparql.types.ast.SelectQueryAST
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
                && body.patterns[0].p == PatternAST.Exact(Quad.NamedTerm("http://example.org/prop"))
                && body.patterns[1].o == PatternAST.Exact(Quad.NamedTerm("http://example.org/test"))
        }
        "SELECT * WHERE { ?s a/<predicate2>*/<predicate3>?o. }" satisfies {
            body.patterns.first().p is PatternAST.Chain
        }
        "SELECT * WHERE { ?s a/?p1*/?p2?o. }" satisfies {
            require(this is SelectQueryAST)
            body.patterns.first().p is PatternAST.Chain
                && output.keys == setOf("s", "p1", "p2", "o")
                && output.entries.all { it.value is SelectQueryAST.BindingOutputEntry }
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
                p = PatternAST.Exact(Quad.NamedTerm("prop")),
                o = PatternAST.Exact(Quad.NamedTerm("value"))
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
            body.optionals.size == 1 &&
            body.unions.size == 1 &&
            output.keys == setOf("s", "content", "value1")
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
            body.optionals.size == 1 &&
            output.keys == setOf("s", "value")
            // TODO: also check the optional's condition
        }
        "select(count(distinct ?s) as ?count){?s?p?o}" satisfies {
            require(this is SelectQueryAST)
            val func =
                (output["count"] as SelectQueryAST.AggregationOutputEntry).aggregation.expression as ExpressionAST.BindingAggregate
            func.type == ExpressionAST.BindingAggregate.Type.COUNT
        }
        "select(avg(?s) + min(?s) / 3 as ?count){?s?p?o}" satisfies {
            require(this is SelectQueryAST)
            (output["count"] as SelectQueryAST.AggregationOutputEntry).aggregation.expression ==
                    ExpressionAST.MathOp.Sum(
                        lhs = ExpressionAST.BindingAggregate(
                            type = ExpressionAST.BindingAggregate.Type.AVG,
                            input = ExpressionAST.BindingValues("s"),
                            distinct = false
                        ),
                        rhs = ExpressionAST.MathOp.Div(
                            lhs = ExpressionAST.BindingAggregate(
                                type = ExpressionAST.BindingAggregate.Type.MIN,
                                input = ExpressionAST.BindingValues("s"),
                                distinct = false
                            ),
                            rhs = ExpressionAST.NumericLiteralValue(3L)
                        )
                    )
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
                p = PatternAST.Exact(Quad.NamedTerm("http://example.com/data/#p")),
                o = PatternAST.Binding("p")
            )
            body.patterns.size == 1 &&
            body.patterns.first() == pattern &&
            ((output["avg"] as SelectQueryAST.AggregationOutputEntry).aggregation.expression as ExpressionAST.BindingAggregate).type == ExpressionAST.BindingAggregate.Type.AVG &&
            (output["c"] as SelectQueryAST.AggregationOutputEntry).aggregation.expression is ExpressionAST.MathOp.Div
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
            "values" in output.keys
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
            output.keys.containsAll(listOf("page, type"))
        }
        /* expected failure cases */
        "SELECT TEST WHERE { ?s a TEST . }" causes CompilerError.Type.SyntaxError
        "SELECT * WHERE { ?s () TEST . }" causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s a ?test " causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s <predicate2>/(<predicate3> ?o2.}" causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s a ?type , }" causes CompilerError.Type.StructuralError
        "PREFIX ex: <http://example.org> SELECT * WHERE { ?s ex:prop/other ?o }" causes CompilerError.Type.SyntaxError
        "prefix ex: <http://example.org> select*{?s dc:title ?o}" causes CompilerError.Type.StructuralError
        "select(count(distinct ?s as ?count){?s?p?o}" causes CompilerError.Type.StructuralError
    }

}
