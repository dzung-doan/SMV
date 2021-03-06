#
# This file is licensed under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import unittest
import sys

from smvbasetest import SmvBaseTest

import pyspark
from pyspark.context import SparkContext
from pyspark.sql import SQLContext, HiveContext
from smv.functions import *

class SmvfuncsTest(SmvBaseTest):
    def test_smvFirst(self):
        df = self.createDF("k:String; t:Integer; v:Double", "z,1,;z,2,1.4;z,5,2.2;a,1,0.3;")

        res = df.groupBy("k").agg(
            smvFirst(df.t, True).alias("first_t"), # use smvFirst instead of Spark's first to test the alternative form also
            smvFirst(df.v, True).alias("first_v"),
            smvFirst(df.v).alias("smvFirst_v")
        )

        exp = self.createDF("k: String;first_t: Integer;first_v: Double;smvFirst_v: Double",
            "a,1,0.3,0.3;" + \
            "z,1,1.4,")

        self.should_be_same(res, exp)

    def test_distMetric(self):
        df = self.createDF("s1:String; s2:String",
            ",ads;" +\
            "asdfg,asdfg;" +\
            "asdfghj,asdfhgj"
        )

        trunc = lambda c: pyspark.sql.functions.round(c,2)
        res = df.select(
            df.s1, df.s2,
            trunc(nGram2(df.s1, df.s2)).alias("nGram2"),
            trunc(nGram3(df.s1, df.s2)).alias("nGram3"),
            trunc(diceSorensen(df.s1, df.s2)).alias("diceSorensen"),
            trunc(normlevenshtein(df.s1, df.s2)).alias("normlevenshtein"),
            trunc(jaroWinkler(df.s1, df.s2)).alias("jaroWinkler")
        )

        exp = self.createDF("s1: String;s2: String;nGram2: Float;nGram3: Float;diceSorensen: Float;normlevenshtein: Float;jaroWinkler: Float",
            ",ads,,,,,;" + \
            "asdfg,asdfg,1.0,1.0,1.0,1.0,1.0;" + \
            "asdfghj,asdfhgj,0.5,0.4,0.5,0.71,0.97")

        self.should_be_same(res, exp)

    def test_smvCreateLookup(self):
        from pyspark.sql.types import StringType
        df = self.createDF("k:String;v:Integer", "a,1;b,2;,3;c,4")
        map_key = smvCreateLookUp({"a":"AA", "b":"BB"}, "__", StringType())
        res = df.withColumn("mapped", map_key(df.k))
        exp = self.createDF("k: String;v: Integer;mapped: String",
            "a,1,AA;" +
            "b,2,BB;" +
            ",3,__;" +
            "c,4,__")
        self.should_be_same(res, exp)
