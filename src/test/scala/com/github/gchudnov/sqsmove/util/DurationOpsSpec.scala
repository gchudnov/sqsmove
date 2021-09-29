package com.github.gchudnov.sqsmove.util

import zio.test.Assertion.*
import zio.test.*
import zio.*
import com.github.gchudnov.sqsmove.util.DurationOps.*
import java.util.concurrent.TimeUnit

object DurationOpsSpec extends DefaultRunnableSpec:
  def spec: ZSpec[Environment, Failure] = suite("DurationOps")(
    test("parse days") {
      val input         = "1d"
      val expected      = Duration(1, TimeUnit.DAYS)
      val errOrDuration = parse(input)
      assert(errOrDuration)(equalTo(Right(expected)))
    },
    test("parse hours") {
      val input         = "2h"
      val expected      = Duration(2, TimeUnit.HOURS)
      val errOrDuration = parse(input)
      assert(errOrDuration)(equalTo(Right(expected)))
    },
    test("parse minutes") {
      val input         = "23m"
      val expected      = Duration(23, TimeUnit.MINUTES)
      val errOrDuration = parse(input)
      assert(errOrDuration)(equalTo(Right(expected)))
    },
    test("parse seconds") {
      val input         = "56s"
      val expected      = Duration(56, TimeUnit.SECONDS)
      val errOrDuration = parse(input)
      assert(errOrDuration)(equalTo(Right(expected)))
    },
    test("parse all components") {
      val input         = "1d12h30m5s"
      val expected      = (Duration(1, TimeUnit.DAYS) + Duration(12, TimeUnit.HOURS) + Duration(30, TimeUnit.MINUTES) + Duration(5, TimeUnit.SECONDS))
      val errOrDuration = parse(input)
      assert(errOrDuration)(equalTo(Right(expected)))
    },
    test("cannot parse garbage") {
      val input         = "1x"
      val errOrDuration = parse(input)
      assert(errOrDuration.isLeft)(equalTo(true))
    },
    test("duration can ve converted to seconds") {
      val d        = (Duration(1, TimeUnit.DAYS) + Duration(12, TimeUnit.HOURS) + Duration(30, TimeUnit.MINUTES) + Duration(5, TimeUnit.SECONDS))
      val expected = 131405
      val actual   = d.getSeconds
      assert(actual)(equalTo(expected))
    },
    test("duration can be converted to a string") {
      val d        = (Duration(1, TimeUnit.DAYS) + Duration(12, TimeUnit.HOURS) + Duration(30, TimeUnit.MINUTES) + Duration(5, TimeUnit.SECONDS))
      val expected = "1d12h30m5s"
      val actual   = asString(d)
      assert(actual)(equalTo(expected))
    }
  )
