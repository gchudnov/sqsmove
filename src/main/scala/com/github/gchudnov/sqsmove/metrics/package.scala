package com.github.gchudnov.sqsmove

import zio.zmx

package object metrics {

  private def metricsMap: Map[String, Double] = {
    import zio.zmx.state.MetricType.Counter
    import zmx.internal.snapshot
    snapshot().values
      .map(it =>
        it.details match {
          case Counter(value) => it.name -> value
          case _              => it.name -> Double.NaN
        }
      )
      .toMap
  }

  def getMetricCount(name: String): Option[Double] =
    metricsMap.get(name)
}
