package com.github.gchudnov.sqsmove.util

import java.io.File
import java.io.{ BufferedWriter, File, FileOutputStream, FileWriter }
import java.nio.file.Files
import java.nio.file.Path

import scala.io.Source
import scala.util.Using
import scala.util.control.Exception.*

object FileOps:

  def readAll(file: File): Either[Throwable, String] =
    allCatch.either {
      Using.resource(Source.fromFile(file)) { file =>
        file.mkString
      }
    }

  def saveString(file: File, data: String): Either[Throwable, Unit] =
    allCatch.either {
      Using.resource(new BufferedWriter(new FileWriter(file))) { writer =>
        writer.write(data)
      }
    }

  def replaceExtension(file: File, newExt: String): File =
    val baseName =
      val originalFileName = file.getName
      if originalFileName.contains(".") then originalFileName.substring(0, originalFileName.lastIndexOf('.'))
      else originalFileName
    val filename = s"${baseName}.${newExt}"
    new File(file.getParentFile, filename)
