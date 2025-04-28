
import TestEnvironment.Companion.test
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.CompilerError
import dev.tesserakt.sparql.types.Expression
import dev.tesserakt.sparql.types.SelectQueryStructure
import dev.tesserakt.sparql.types.TriplePattern
import kotlin.test.Test

class CompilerTest {

    @Test
    fun select() = test {
        /* content tests */
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o ; }" satisfies {
            val pattern = TriplePattern(
                s = TriplePattern.NamedBinding("s"),
                p = TriplePattern.NamedBinding("p"),
                o = TriplePattern.NamedBinding("o")
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
                && body.patterns[0].p == TriplePattern.Exact(Quad.NamedTerm("http://example.org/prop"))
                && body.patterns[1].o == TriplePattern.Exact(Quad.NamedTerm("http://example.org/test"))
        }
        "SELECT * WHERE { ?s a/<predicate2>*/<predicate3>?o. }" satisfies {
            body.patterns.first().p is TriplePattern.UnboundSequence
        }
        "SELECT * WHERE { ?s a/?p1*/?p2?o. }" causes CompilerError.Type.StructuralError
        "SELECT * WHERE { ?s (<predicate2>|<predicate3>)?o. }" satisfies {
            body.patterns.first().p is TriplePattern.SimpleAlts
        }
        "SELECT * WHERE { ?s <contains>/(<prop1>|!<prop2>)* ?o2 }" satisfies {
            body.patterns.first().p.let { p -> p is TriplePattern.UnboundSequence && p.chain[1] is TriplePattern.ZeroOrMore }
        }
        "SELECT ?s?p?o WHERE {?s?p?o2;?p2?o.}" satisfies {
            body.patterns.size == 2 && body.patterns[1].p == TriplePattern.NamedBinding("p2")
        }
        "SELECT ?s WHERE {?s<prop><value>}" satisfies {
            val pattern = TriplePattern(
                s = TriplePattern.NamedBinding("s"),
                p = TriplePattern.Exact(Quad.NamedTerm("prop")),
                o = TriplePattern.Exact(Quad.NamedTerm("value"))
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
            require(this is SelectQueryStructure)
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
            require(this is SelectQueryStructure)
            body.patterns.size == 1 &&
            body.optional.size == 1 &&
            bindings == setOf("s", "value")
            // TODO: also check the optional's condition
        }
        "select(count(distinct ?s) as ?count){?s?p?o}" satisfies {
            require(this is SelectQueryStructure)
            val count = output!!.find { it.name == "count" }!!
            val func =
                (count as SelectQueryStructure.ExpressionOutput).expression as Expression.BindingAggregate
            func.type == Expression.BindingAggregate.Type.COUNT
        }
        "select(avg(?s) + min(?s) / 3 as ?count){?s?p?o}" satisfies {
            require(this is SelectQueryStructure)
            val count = output!!.find { it.name == "count" }!!
            (count as SelectQueryStructure.ExpressionOutput).expression ==
                    Expression.MathOp(
                        lhs = Expression.BindingAggregate(
                            type = Expression.BindingAggregate.Type.AVG,
                            input = Expression.BindingValues("s"),
                            distinct = false
                        ),
                        rhs = Expression.MathOp(
                            lhs = Expression.BindingAggregate(
                                type = Expression.BindingAggregate.Type.MIN,
                                input = Expression.BindingValues("s"),
                                distinct = false
                            ),
                            rhs = Expression.NumericLiteralValue(3L),
                            operator = Expression.MathOp.Operator.DIV
                        ),
                        operator = Expression.MathOp.Operator.SUM
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
            require(this is SelectQueryStructure)
            val pattern = TriplePattern(
                s = TriplePattern.NamedBinding("g"),
                p = TriplePattern.Exact(Quad.NamedTerm("http://example.com/data/#p")),
                o = TriplePattern.NamedBinding("p")
            )
            val avg = output!!.find { it.name == "avg" }
            val c = output!!.find { it.name == "c" }
            body.patterns.size == 1 &&
            body.patterns.first() == pattern &&
            ((avg as SelectQueryStructure.ExpressionOutput).expression as Expression.BindingAggregate).type == Expression.BindingAggregate.Type.AVG &&
            (c as SelectQueryStructure.ExpressionOutput).expression is Expression.MathOp
        }
        """
            SELECT * WHERE {
                ?s ?p [ a <type> ; ]
            }
        """ satisfies {
            body.patterns.size == 2 &&
            this is SelectQueryStructure &&
            // the generated binding should not be visible!
            bindings.size == 2
        }
        """
            SELECT * WHERE {
                ?s ?p [ <contains> [ <data> ?values ] ; ]
            }
        """ satisfies {
            require(this is SelectQueryStructure)

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
            require(this is SelectQueryStructure)
            body.patterns.size == 7 &&
            // the generated binding should not be visible!
            bindings == setOf("s", "p", "data1", "data2", "data3")
        }
        /* expected failure case as it has (currently) unsupported language tags */
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
        """ causes CompilerError.Type.SyntaxError
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

    @Test
    fun filters() = test {
        // source: https://www.w3.org/TR/sparql11-query/
        // a regular expression filter, but inside an optional graph pattern
        """
            PREFIX  dc:  <http://purl.org/dc/elements/1.1/>
            PREFIX  ns:  <http://example.org/ns#>
            SELECT  ?title ?price
            WHERE {
                ?x dc:title ?title .
                OPTIONAL { ?x ns:price ?price . FILTER (?price < 30) }
            }
        """ satisfies {
            true
        }
        // a graph pattern filter
        """
            PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
            PREFIX  foaf:   <http://xmlns.com/foaf/0.1/> 

            SELECT ?person
            WHERE 
            {
                ?person rdf:type  foaf:Person .
                FILTER EXISTS { ?person foaf:name ?name }
            }
        """ satisfies {
            true
        }
        // an inversion of a graph pattern filter
        """
            PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
            PREFIX  foaf:   <http://xmlns.com/foaf/0.1/> 

            SELECT ?person
            WHERE 
            {
                ?person rdf:type  foaf:Person .
                FILTER NOT EXISTS { ?person foaf:name ?name }
            }
        """ satisfies {
            true
        }
        // an inversion of a graph pattern filter, having a filter of its own
        """
            PREFIX : <http://example.com/>
            SELECT * WHERE {
                ?x :p ?n
                FILTER NOT EXISTS {
                    ?x :q ?m .
                    FILTER(?n = ?m)
                }
            }
        """ satisfies {
            true
        }
    }

}
