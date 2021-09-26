package com.github.gchudnov.sqsmove.csvs

object CsvOps:

  private val CellSeparator = ","
  private val RowSeparator  = sys.props("line.separator")

  def asString(t: List[List[String]]): String =
    t.map(l => l.map(it => s""""${it}"""").mkString(CellSeparator)).mkString(RowSeparator)
