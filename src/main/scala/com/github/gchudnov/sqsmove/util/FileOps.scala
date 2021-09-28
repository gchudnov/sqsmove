package com.github.gchudnov.sqsmove.util

import java.io.File
import java.io.{ BufferedWriter, File, FileOutputStream, FileWriter }
import java.nio.file.Files
import java.nio.file.Path

import scala.io.Source
import scala.util.Using
import scala.util.control.Exception.*

object FileOps:

  def createDir(dir: Path): Either[Throwable, File] =
    allCatch.either {
      Files.createDirectory(dir).toFile
    }

  def stringFromFile(file: File): Either[Throwable, String] =
    allCatch.either {
      Using.resource(Source.fromFile(file)) { file =>
        file.getLines().mkString("\n").trim()
      }
    }

  def saveString(file: File, data: String): Either[Throwable, Unit] =
    allCatch.either {
      Using.resource(new BufferedWriter(new FileWriter(file))) { writer =>
        writer.write(data)
      }
    }

  def replaceExtension(file: File, extension: String): File =
    val filename = {
      val originalFileName = file.getName
      if originalFileName.contains(".") then originalFileName.substring(0, originalFileName.lastIndexOf('.'))
      else originalFileName
    } + "." + extension

    new File(file.getParentFile, filename)
