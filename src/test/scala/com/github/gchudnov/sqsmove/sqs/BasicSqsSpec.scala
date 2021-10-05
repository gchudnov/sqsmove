package com.github.gchudnov.sqsmove.sqs

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.sqs.BasicSqs.*
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.core.SdkBytes
import scala.jdk.CollectionConverters.*
import com.github.gchudnov.sqsmove.util.DirOps.*
import com.github.gchudnov.sqsmove.util.FileOps.*
import java.io.File

object BasicSqsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("BasicSqs")(
    test("attributes can be decoded") {
      val m = Map[String, MessageAttributeValue](
        "strAttr" -> MessageAttributeValue.builder().stringValue("str").dataType("String").build(),
        "numAttr" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build(),
        "binAttr" -> MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String("ABC")).dataType("Binary").build()
      )

      val actual = attrsToTable(m)
      val expected = List(
        List("name", "type", "value"),
        List("strAttr", "String", "str"),
        List("numAttr", "Number", "1"),
        List("binAttr", "Binary", "QUJD")
      )

      assert(actual)(equalTo(expected))
    },
    test("attributes can be encoded") {
      val t = List(
        List("name", "type", "value"),
        List("strAttr", "String", "str"),
        List("numAttr", "Number", "1"),
        List("binAttr", "Binary", "QUJD")
      )

      val errOrActual = attrsFromTable(t)
      val expected = Map[String, MessageAttributeValue](
        "strAttr" -> MessageAttributeValue.builder().stringValue("str").dataType("String").build(),
        "numAttr" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build(),
        "binAttr" -> MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String("QUJD")).dataType("Binary").build()
      )

      assert(errOrActual)(equalTo(Right(expected)))
    },
    test("attributes are encoded to the empty map if input is empty") {
      val t = List.empty[List[String]]

      val errOrActual = attrsFromTable(t)
      val expected    = Map.empty[String, MessageAttributeValue]

      assert(errOrActual)(equalTo(Right(expected)))
    },
    test("data and meta can be converted to a message") {
      val data = "123"
      val meta = """name,type,value
                   |strAttr,String,str
                   |numAttr,Number,1
                   |binAttr,Binary,QUJD
                   |""".stripMargin
      val errOrActual = toMessage(data, Some(meta))

      val expectedBody = data
      val expectedAttrs = Map[String, MessageAttributeValue](
        "strAttr" -> MessageAttributeValue.builder().stringValue("str").dataType("String").build(),
        "numAttr" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build(),
        "binAttr" -> MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String("QUJD")).dataType("Binary").build()
      )

      assert(errOrActual.map(_.body))(equalTo(Right(expectedBody))) &&
      assert(errOrActual.map(_.messageAttributes.asScala.toMap))(equalTo(Right(expectedAttrs)))
    },
    test("message is created from a file when there is no metadata") {
      val data = "123"

      val errOrFile = for
        d1 <- newTmpDir("msg-no-meta")
        f1  = new File(d1, "msg")
        _  <- saveString(f1, data)
      yield f1

      val expectedAttrs = Map.empty[String, MessageAttributeValue]

      for
        f <- ZIO.fromEither(errOrFile)
        m <- messageFromFile(f)
      yield assert(m.body)(equalTo(data)) && assert(m.messageAttributes.asScala.toMap)(equalTo(expectedAttrs))
    },
    test("message is created from a file when there is metadata") {
      val data = "123"
      val meta = """name,type,value
                   |strAttr,String,str
                   |numAttr,Number,1
                   |binAttr,Binary,QUJD
                   |""".stripMargin

      val errOrFile = for
        d1 <- newTmpDir("msg-and-meta")
        f1  = new File(d1, "msg")
        f2  = new File(d1, "msg.meta")
        _  <- saveString(f1, data)
        _  <- saveString(f2, meta)
      yield f1

      val expectedAttrs = Map[String, MessageAttributeValue](
        "strAttr" -> MessageAttributeValue.builder().stringValue("str").dataType("String").build(),
        "numAttr" -> MessageAttributeValue.builder().stringValue("1").dataType("Number").build(),
        "binAttr" -> MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String("QUJD")).dataType("Binary").build()
      )

      for
        f <- ZIO.fromEither(errOrFile)
        m <- messageFromFile(f)
      yield assert(m.body)(equalTo(data)) && assert(m.messageAttributes.asScala.toMap)(equalTo(expectedAttrs))
    },
    test("message is created from a file when there is metadata but the file is empty") {
      val data = "123"
      val meta = ""

      val errOrFile = for
        d1 <- newTmpDir("msg-and-meta")
        f1  = new File(d1, "msg")
        f2  = new File(d1, "msg.meta")
        _  <- saveString(f1, data)
        _  <- saveString(f2, meta)
      yield f1

      val expectedAttrs = Map.empty[String, MessageAttributeValue]

      for
        f <- ZIO.fromEither(errOrFile)
        m <- messageFromFile(f)
      yield assert(m.body)(equalTo(data)) && assert(m.messageAttributes.asScala.toMap)(equalTo(expectedAttrs))
    },
    test("find column index can be found if exists") {
      val header = List("aaa", "bbb", "ccc")
      val name   = "bbb"

      val actual   = findColumnIndex(header, name)
      val expected = 1

      assert(actual)(equalTo(Some(expected)))
    }
  )
