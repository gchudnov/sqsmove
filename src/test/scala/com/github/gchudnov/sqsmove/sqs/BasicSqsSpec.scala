package com.github.gchudnov.sqsmove.sqs

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.sqs.BasicSqs.*
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.core.SdkBytes

object BasicSqsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("BasicSqs")(
    test("attributes can be decoded") {
      val m = Map[String, MessageAttributeValue](
        "strAttr" -> MessageAttributeValue.builder().stringValue("str").dataType("String").build(),
        "numAttr" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build(),
        "binAttr" -> MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String("ABC")).dataType("Binary").build()
      )

      val actual = toTable(m)
      val expected = List(
        List("name", "type", "value"),
        List("strAttr", "String", "\"str\""),
        List("numAttr", "Number", "1"),
        List("binAttr", "Binary", "QUJD")
      )

      assert(actual)(equalTo(expected))
    },
    test("attributes can be encoded") {
      val t = List(
        List("name", "type", "value"),
        List("strAttr", "String", "\"str\""),
        List("numAttr", "Number", "1"),
        List("binAttr", "Binary", "QUJD")
      )

      val errOrActual = fromTable(t)
      val expected = Map[String, MessageAttributeValue](
        "strAttr" -> MessageAttributeValue.builder().stringValue("str").dataType("String").build(),
        "numAttr" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build(),
        "binAttr" -> MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String("QUJD")).dataType("Binary").build()
      )

      assert(errOrActual)(equalTo(Right(expected)))
    }
  )
