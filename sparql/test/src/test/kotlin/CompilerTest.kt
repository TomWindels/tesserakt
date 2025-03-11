
import TestEnvironment.Companion.test
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.CompilerError
import dev.tesserakt.sparql.types.runtime.element.Expression
import dev.tesserakt.sparql.types.runtime.element.Pattern
import dev.tesserakt.sparql.types.runtime.element.SelectQuery
import kotlin.test.Test

class CompilerTest {

    @Test
    fun select() = test {
        /* content tests */
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o ; }" satisfies {
            val pattern = Pattern(
                s = Pattern.NamedBinding("s"),
                p = Pattern.NamedBinding("p"),
                o = Pattern.NamedBinding("o")
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
                && body.patterns[0].p == Pattern.Exact(Quad.NamedTerm("http://example.org/prop"))
                && body.patterns[1].o == Pattern.Exact(Quad.NamedTerm("http://example.org/test"))
        }
        "SELECT * WHERE { ?s a/<predicate2>*/<predicate3>?o. }" satisfies {
            body.patterns.first().p is Pattern.UnboundSequence
        }
        "SELECT * WHERE { ?s a/?p1*/?p2?o. }" causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s (<predicate2>|<predicate3>)?o. }" satisfies {
            body.patterns.first().p is Pattern.SimpleAlts
        }
        "SELECT * WHERE { ?s <contains>/(<prop1>|!<prop2>)* ?o2 }" satisfies {
            body.patterns.first().p.let { p -> p is Pattern.UnboundSequence && p.chain[1] is Pattern.ZeroOrMore }
        }
        "SELECT ?s?p?o WHERE {?s?p?o2;?p2?o.}" satisfies {
            body.patterns.size == 2 && body.patterns[1].p == Pattern.NamedBinding("p2")
        }
        "SELECT ?s WHERE {?s<prop><value>}" satisfies {
            val pattern = Pattern(
                s = Pattern.NamedBinding("s"),
                p = Pattern.Exact(Quad.NamedTerm("prop")),
                o = Pattern.Exact(Quad.NamedTerm("value"))
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
            require(this is SelectQuery)
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            body.unions.size == 1 &&
            bindings == setOf("s", "content", "value1")
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
            require(this is SelectQuery)
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            bindings == setOf("s", "value")
            // TODO: also check the optional's condition
        }
        "select(count(distinct ?s) as ?count){?s?p?o}" satisfies {
            require(this is SelectQuery)
            val count = output!!.find { it.name == "count" }!!
            val func =
                (count as SelectQuery.ExpressionOutput).expression as Expression.BindingAggregate
            func.type == Expression.BindingAggregate.Type.COUNT
        }
        "select(avg(?s) + min(?s) / 3 as ?count){?s?p?o}" satisfies {
            require(this is SelectQuery)
            val count = output!!.find { it.name == "count" }!!
            (count as SelectQuery.ExpressionOutput).expression ==
                    Expression.MathOp.Sum(
                        lhs = Expression.BindingAggregate(
                            type = Expression.BindingAggregate.Type.AVG,
                            input = Expression.BindingValues("s"),
                            distinct = false
                        ),
                        rhs = Expression.MathOp.Div(
                            lhs = Expression.BindingAggregate(
                                type = Expression.BindingAggregate.Type.MIN,
                                input = Expression.BindingValues("s"),
                                distinct = false
                            ),
                            rhs = Expression.NumericLiteralValue(3L)
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
            require(this is SelectQuery)
            val pattern = Pattern(
                s = Pattern.NamedBinding("g"),
                p = Pattern.Exact(Quad.NamedTerm("http://example.com/data/#p")),
                o = Pattern.NamedBinding("p")
            )
            val avg = output!!.find { it.name == "avg" }
            val c = output!!.find { it.name == "c" }
            body.patterns.size == 1 &&
            body.patterns.first() == pattern &&
            ((avg as SelectQuery.ExpressionOutput).expression as Expression.BindingAggregate).type == Expression.BindingAggregate.Type.AVG &&
            (c as SelectQuery.ExpressionOutput).expression is Expression.MathOp.Div
        }
        """
            SELECT * WHERE {
                ?s ?p [ a <type> ; ]
            }
        """ satisfies {
            body.patterns.size == 2 &&
            this is SelectQuery &&
            // the generated binding should not be visible!
            bindings.size == 2
        }
        """
            SELECT * WHERE {
                ?s ?p [ <contains> [ <data> ?values ] ; ]
            }
        """ satisfies {
            require(this is SelectQuery)

            body.patterns.size == 3 &&
            // the generated binding should not be visible!
            bindings == setOf("s", "p", "values")
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
            require(this is SelectQuery)
            body.patterns.size == 7 &&
            // the generated binding should not be visible!
            bindings == setOf("s", "p", "data1", "data2", "data3")
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
            require(this is SelectQuery)
            bindings.containsAll(listOf("page, type"))
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
