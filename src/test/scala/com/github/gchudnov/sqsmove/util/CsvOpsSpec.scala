package com.github.gchudnov.sqsmove.util

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.util.ArrayOps.*

object CsvOpsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("CsvOps")(
    test("Table can be serialized to a string as CSV") {
      val t = List(
        List("name", "type", "value"),
        List("strAttr", "String", "str"),
        List("numAttr", "Number", "1"),
        List("binAttr", "Binary", "QUJD")
      )
      val actual = CsvOps.csvToString(t)
      val expected = """name,type,value
                       |strAttr,String,str
                       |numAttr,Number,1
                       |binAttr,Binary,QUJD
                       |""".stripMargin

      assert(actual)(equalTo(expected))
    },
    test("if a cell has special characters, it is escaped") {
      val t = List(
        List("name", "type", "value"),
        List("strAttr", "String", "{ \"k1: \"v1\", \"k2: \"v2\" }")
      )
      val actual = CsvOps.csvToString(t)
      val expected = """name,type,value
                       |strAttr,String,"{ ""k1: ""v1"", ""k2: ""v2"" }"
                       |""".stripMargin

      assert(actual)(equalTo(expected))
    },
    test("CSV string can be deserialized to a table") {
      val data = """name,type,value
                   |strAttr,String,"str"
                   |numAttr,Number,1
                   |binAttr,Binary,QUJD
                   |""".stripMargin

      val actual   = CsvOps.csvFromString(data)
      val expected = List(List("name", "type", "value"), List("strAttr", "String", "str"), List("numAttr", "Number", "1"), List("binAttr", "Binary", "QUJD"))

      assert(actual)(equalTo(Right(expected)))
    }
  )
