/*
 * This file is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tresamigos.smv {

import org.apache.spark.sql.SchemaRDD


class SmvTestFile(override val _name: String) extends SmvFile(_name, null, null) {
  override def rdd(app: SmvApp): SchemaRDD = null
}

class SmvAppTest extends SparkTestUtil {

  val fx = new SmvTestFile("FX")

  object A extends SmvModule("A", "A Module") {
    var moduleRunCount = 0
    override def requires() = Seq("FX")
    override def run(inputs: Map[String, SchemaRDD]) = {
      moduleRunCount = moduleRunCount + 1
      require(inputs.size === 1)
      createSchemaRdd("a:Integer", "1;2;3")
    }
  }

  object B extends SmvModule("B", "B Module") {
    override def requires() = Seq("A")
    override def run(inputs: Map[String, SchemaRDD]) = {
      val sc = inputs("A").sqlContext; import sc._
      require(inputs.size === 1)
      inputs("A").selectPlus('a + 1 as 'b)
    }
  }

  object C extends SmvModule("C", "C Module") {
    override def requires() = Seq("A", "B")
    override def run(inputs: Map[String, SchemaRDD]) = {
      val sc = inputs("A").sqlContext; import sc._
      require(inputs.size === 2)
      inputs("B").selectPlus('b + 1 as 'c)
    }
  }

  sparkTest("Test normal dependency execution") {
    object app extends SmvApp("test dependency", Option(sc)) {
      override def getDataSets() = Seq(fx, A, B, C)
    }

    val res = app.resolveRDD("C")
    assertSrddDataEqual(res, "1,2,3;2,3,4;3,4,5")

    // even though both B and C depended on A, A should have only run once!
    assert(A.moduleRunCount === 1)
  }

  object A_cycle extends SmvModule("A", "A Cycle") {
    override def requires() = Seq("B")
    override def run(inputs: Map[String, SchemaRDD]) = null
  }

  object B_cycle extends SmvModule("B", "B Cycle") {
    override def requires() = Seq("A")
    override def run(inputs: Map[String, SchemaRDD]) = null
  }

  sparkTest("Test cycle dependency execution") {
    object app extends SmvApp("test dependency", Option(sc)) {
      override def getDataSets() = Seq(A_cycle, B_cycle)
    }

    intercept[IllegalStateException] {
      app.resolveRDD("B")
    }
  }

  sparkTest("Test name not found") {
    object app extends SmvApp("test dependency", Option(sc)) {
      override def getDataSets() = Seq(fx, A, B)
    }

    intercept[NoSuchElementException] {
      app.resolveRDD("X")
    }
  }

  sparkTest("Test modulesInPackage method.") {
    object app extends SmvApp("test modulesInPackage", Option(sc)) {
      override def getDataSets() = Seq.empty
    }
    val modNames: Seq[String] = app.modulesInPackage("org.tresamigos.smv.smvAppTestPackage").map(_.name)
    assertUnorderedSeqEqual(modNames, Seq("X", "Y"))
  }

  sparkTest("Test dependency graph creation.") {
    object app extends SmvApp("test dependency graph", Option(sc)) {
      override def getDataSets() = Seq(fx, A, B, C)
    }

    val depGraph = new SmvModuleDependencyGraph("C", app)
    //depGraph.saveToFile("foo.dot")

    val edges = depGraph.graph
    assert(edges.size === 4)
    assert(edges("FX") === Set())
    assert(edges("A") === Set("FX"))
    assert(edges("B") === Set("A"))
    assert(edges("C") === Set("A", "B"))
  }
}
}

/**
 * package below is used for testing the modulesInPackage method in SmvApp.
 */
package org.tresamigos.smv.smvAppTestPackage {

  import org.apache.spark.sql.SchemaRDD
  import org.tresamigos.smv.SmvModule

  object X extends SmvModule("X", "X Module") {
    override def requires() = Seq.empty
    override def run(inputs: Map[String, SchemaRDD]) = null
  }

  object Y extends SmvModule("Y", "Y Module") {
    override def requires() = Seq("X")
    override def run(inputs: Map[String, SchemaRDD]) = null
  }

  // should still work even if we have a class X.
  class X

  // should not show as a valid module because it is a class and not an object instance.
  class Z extends SmvModule("Z", "Z Class") {
    override def requires = Seq()
    override def run(inputs: Map[String, SchemaRDD]) = null
  }
}