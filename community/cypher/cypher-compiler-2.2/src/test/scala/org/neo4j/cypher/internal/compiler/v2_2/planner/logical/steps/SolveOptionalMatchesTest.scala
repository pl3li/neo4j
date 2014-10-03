/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v2_2.planner.logical.steps


import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_2.ast
import org.neo4j.cypher.internal.compiler.v2_2.planner.logical.plans._
import org.neo4j.cypher.internal.compiler.v2_2.planner.logical.{Cardinality, LogicalPlanningContext, PlanTable}
import org.neo4j.cypher.internal.compiler.v2_2.planner.{LogicalPlanningTestSupport, PlannerQuery, QueryGraph}
import org.neo4j.graphdb.Direction
import org.mockito.Matchers._
import org.mockito.Mockito._

class SolveOptionalMatchesTest extends CypherFunSuite with LogicalPlanningTestSupport {

  val patternRelAtoB = newPatternRelationship("a", "b", "r1")
  val patternRelAtoC = newPatternRelationship("a", "c", "r2")
  val patternRelCtoX = newPatternRelationship("c", "x", "r3")

  // GIVEN (a) OPTIONAL MATCH (a)-[r1]->(b)
  val qgForAtoB = QueryGraph(
    patternNodes = Set("a", "b"),
    patternRelationships = Set(patternRelAtoB)
  )

  // GIVEN (a) OPTIONAL MATCH (a)-[r2]->(c)
  val qgForAtoC = QueryGraph(
    patternNodes = Set("a", "c"),
    patternRelationships = Set(patternRelAtoC)
  )

  // GIVEN (c) OPTIONAL MATCH (c)-[r3]->(x)
  val qgForCtoX = QueryGraph(
    patternNodes = Set("c", "x"),
    patternRelationships = Set(patternRelCtoX)
  )

  test("should introduce apply for unsolved optional match when all arguments are covered") {
    // Given MATCH a OPTIONAL MATCH (a)-[r]->(b)
    val qg = QueryGraph(patternNodes = Set("a")).withAddedOptionalMatch(qgForAtoB)

    implicit val context = createLogicalPlanContext()
    val lhs = newMockedLogicalPlan("a")
    val planTable = PlanTable(Map(Set(IdName("a")) -> lhs))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    val expectedRhs = Expand(SingleRow(Set("a"))(PlannerQuery.empty)(), "a", Direction.OUTGOING, Direction.OUTGOING, Seq.empty, "b", "r1", SimplePatternLength)(PlannerQuery.empty)
    val expectedResult = Apply(lhs, Optional(expectedRhs)(PlannerQuery.empty))(PlannerQuery.empty)

    resultingPlanTable.plans should have size 1
    resultingPlanTable.plans.head should equal(expectedResult)
  }

  test("should not try to solve optional match if cross product still needed") {
    // Given
    val qg = QueryGraph(
      patternNodes = Set("a", "b") // MATCH a, b
    ).withAddedOptionalMatch(qgForAtoB.withArgumentIds(Set("a", "b"))) // GIVEN a, b OPTIONAL MATCH a-[r]->b

    implicit val context = createLogicalPlanContext()
    val planForA = newMockedLogicalPlan("a")
    val planForB = newMockedLogicalPlan("b")
    val planTable = PlanTable(Map(
      Set(IdName("a")) -> planForA,
      Set(IdName("b")) -> planForB)
    )

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    resultingPlanTable should equal(planTable)
  }

  test("should not try to solve optional match if already solved by input plan") {
    // Given
    val qg = QueryGraph(patternNodes = Set("a")).withAddedOptionalMatch(qgForAtoB)

    implicit val context = createLogicalPlanContext()
    val inputPlan = newMockedLogicalPlanWithSolved(
      Set("a", "b"),
      PlannerQuery.empty.withGraph(qg))

    val planTable = PlanTable(Map(Set(IdName("a")) -> inputPlan))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    resultingPlanTable should equal(planTable)
  }

  test("when given two optional matches, and one is already solved, will solve the next") {
    // Given MATCH a OPTIONAL MATCH a-->b OPTIONAL MATCH a-->c
    val qgWithFirstOptionalMatch = QueryGraph(patternNodes = Set("a")).
                                   withAddedOptionalMatch(qgForAtoB)
    val qg = qgWithFirstOptionalMatch.
             withAddedOptionalMatch(qgForAtoC)

    implicit val context = createLogicalPlanContext()
    val inputPlan = newMockedLogicalPlanWithSolved(
      Set("a", "b"),
      PlannerQuery.empty.withGraph(qgWithFirstOptionalMatch))

    val planTable = PlanTable(Map(Set(IdName("a"), IdName("b")) -> inputPlan))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    val innerPlan = Expand(SingleRow(Set("a"))(PlannerQuery.empty)(), "a", Direction.OUTGOING, Direction.OUTGOING, Seq.empty, "c", "r2", SimplePatternLength)(PlannerQuery.empty)
    val applyPlan = Apply(inputPlan, Optional(innerPlan)(PlannerQuery.empty))(PlannerQuery.empty)

    resultingPlanTable.plans should have size 1
    resultingPlanTable.plans.head should equal(applyPlan)
  }

  test("test OPTIONAL MATCH a RETURN a") {
    // OPTIONAL MATCH a
    val qg = QueryGraph.empty.withOptionalMatches(Seq(QueryGraph(patternNodes = Set(IdName("a")))))
    implicit val context = createLogicalPlanContext()
    val planTable = PlanTable()

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    val singleRow: LogicalPlan = SingleRow(Set.empty)(PlannerQuery.empty)()
    val expectedRhs: LogicalPlan = AllNodesScan(IdName("a"), Set.empty)(PlannerQuery.empty)
    val applyPlan = Apply(singleRow, Optional(expectedRhs)(PlannerQuery.empty))(PlannerQuery.empty)

    resultingPlanTable.plans should have size 1
    resultingPlanTable.plans.head should equal(applyPlan)
  }

  test("two optional matches to solve - the first one is solved and the second is N/A") {
    // Given MATCH a, c OPTIONAL MATCH a-->b OPTIONAL MATCH a-->c
    val qgWithFirstOptionalMatch = QueryGraph(patternNodes = Set("a", "c")).
                                   withAddedOptionalMatch(qgForAtoB)
    val qg = qgWithFirstOptionalMatch.
             withAddedOptionalMatch(qgForAtoC)

    implicit val context = createLogicalPlanContext()
    val lhs = newMockedLogicalPlanWithSolved(
      Set("a", "b", "r1"),
      PlannerQuery.empty.withGraph(qgWithFirstOptionalMatch))

    val planForC = newMockedLogicalPlan("c")

    val planTable = PlanTable(Map(
      Set(IdName("a"), IdName("b"), IdName("r1")) -> lhs,
      Set(IdName("c")) -> planForC
    ))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    resultingPlanTable should equal(planTable)
  }

  test("should handle multiple optional matches building off of different plans in the plan table") {
    // Given MATCH a, c OPTIONAL MATCH a-->b OPTIONAL MATCH a-->c
    val qgWithFirstOptionalMatch = QueryGraph(patternNodes = Set("a", "c")).
                                   withAddedOptionalMatch(qgForAtoB)
    val qg = qgWithFirstOptionalMatch.
             withAddedOptionalMatch(qgForAtoC)

    implicit val context = createLogicalPlanContext()
    val lhs = newMockedLogicalPlanWithSolved(
      Set("a", "b", "r1"),
      PlannerQuery.empty.withGraph(qgWithFirstOptionalMatch))

    val planForC = newMockedLogicalPlan("c")

    val planTable = PlanTable(Map(
      Set(IdName("a"), IdName("b"), IdName("r1")) -> lhs,
      Set(IdName("c")) -> planForC
    ))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then
    resultingPlanTable should equal(planTable)
  }

  test("given plan table with two plans, and the ability to solve one optional match based on a plan, do it") {
    // MATCH a, c OPTIONAL MATCH a-->b
    val qg = QueryGraph(patternNodes = Set("a", "c")).withAddedOptionalMatch(qgForAtoB)
    implicit val context = createLogicalPlanContext()
    val planForA = newMockedLogicalPlan("a")
    val planForC = newMockedLogicalPlan("c")

    val planTable = PlanTable(Map(
      Set(IdName("c")) -> planForC,
      Set(IdName("a")) -> planForA
    ))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)
    val expectedRhs = Expand(SingleRow(Set("a"))(PlannerQuery.empty)(), "a", Direction.OUTGOING, Direction.OUTGOING, Seq.empty, "b", "r1", SimplePatternLength)(PlannerQuery.empty)
    val expectedResult = Apply(planForA, Optional(expectedRhs)(PlannerQuery.empty))(PlannerQuery.empty)

    resultingPlanTable.plans should have size 2
    resultingPlanTable.m should contain(Set(IdName("a"), IdName("b"), IdName("r1")) -> expectedResult)
  }

  test("given plan table with multiple plans, and multiple optional matches that can be built on top of them, solve them all") {
    // MATCH a, c OPTIONAL MATCH a-->b OPTIONAL MATCH c-->x
    val qg = QueryGraph(patternNodes = Set("a", "c")).
    withAddedOptionalMatch(qgForAtoB).
    withAddedOptionalMatch(qgForCtoX)


    implicit val context = createLogicalPlanContext()
    val planForA = newMockedLogicalPlan("a")
    val planForC = newMockedLogicalPlan("c")

    val planTable = PlanTable(Map(
      Set(IdName("c")) -> planForC,
      Set(IdName("a")) -> planForA
    ))

    // When
    val step1 = solveOptionalMatches(planTable, qg)
    val resultingPlanTable = solveOptionalMatches(step1, qg)

    val expectedPlanForAtoB = Expand(SingleRow(Set("a"))(PlannerQuery.empty)(), "a", Direction.OUTGOING, Direction.OUTGOING, Seq.empty, "b", "r1", SimplePatternLength)(PlannerQuery.empty)
    val expectedResult1 = Apply(planForA, Optional(expectedPlanForAtoB)(PlannerQuery.empty))(PlannerQuery.empty)

    val expectedPlanForCtoX = Expand(SingleRow(Set("c"))(PlannerQuery.empty)(), "c", Direction.OUTGOING, Direction.OUTGOING, Seq.empty, "x", "r3", SimplePatternLength)(PlannerQuery.empty)
    val expectedResult2 = Apply(planForC, Optional(expectedPlanForCtoX)(PlannerQuery.empty))(PlannerQuery.empty)

    resultingPlanTable.plans should have size 2
    resultingPlanTable.m should contain(Set(IdName("a"), IdName("b"), IdName("r1")) -> expectedResult1)
    resultingPlanTable.m should contain(Set(IdName("c"), IdName("x"), IdName("r3")) -> expectedResult2)
  }

  test("should introduce apply for unsolved optional match and solve predicates on the pattern") {
    // Given MATCH a OPTIONAL MATCH (a)-[r]->(b:X)
    val labelPredicate: ast.Expression = ast.HasLabels(ident("b"), Seq(ast.LabelName("X")_ )) _
    val qg = QueryGraph(patternNodes = Set("a")).
             withAddedOptionalMatch(qgForAtoB.addPredicates(labelPredicate))

    val factory = newMockedMetricsFactory
    when(factory.newCardinalityEstimator(any(), any(), any())).thenReturn((plan: LogicalPlan) => plan match {
      case _: SingleRow => Cardinality(1.0)
      case _            => Cardinality(1000.0)
    })

    implicit val context = newMockedLogicalPlanningContext(
      planContext = newMockedPlanContext,
      metrics = factory.newMetrics(hardcodedStatistics, newMockedSemanticTable)
    )
    val lhs = newMockedLogicalPlan("a")
    val planTable = PlanTable(Map(Set(IdName("a")) -> lhs))

    // When
    val resultingPlanTable = solveOptionalMatches(planTable, qg)

    // Then

    val arguments = SingleRow(Set("a"))(PlannerQuery.empty)()
    val expand = Expand(
      arguments,
      "a", Direction.OUTGOING, Direction.OUTGOING, Seq.empty, "b", "r1", SimplePatternLength)(PlannerQuery.empty)
    val expectedRhs = Selection(Seq(labelPredicate), expand)(PlannerQuery.empty)

    val expectedResult = Apply(lhs, Optional(expectedRhs)(PlannerQuery.empty))(PlannerQuery.empty)

    resultingPlanTable.plans should have size 1
    resultingPlanTable.plans.head should equal(expectedResult)
  }


  private def createLogicalPlanContext(): LogicalPlanningContext = {
    val factory = newMockedMetricsFactory
    newMockedLogicalPlanningContext(
      planContext = newMockedPlanContext,
      metrics = factory.newMetrics(hardcodedStatistics, newMockedSemanticTable)
    )
  }
}
