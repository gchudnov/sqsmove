package com.github.gchudnov.sqsmove.util

import java.util.Base64
import scala.util.control.Exception.*

object ArrayOps:

  def bytesToBase64(value: Array[Byte]): String =
    new String(Base64.getEncoder().encode(value))

  def base64ToBytes(value: String): Either[Throwable, Array[Byte]] = allCatch.either {
    Base64.getDecoder().decode(value)
  }
