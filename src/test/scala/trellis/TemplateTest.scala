package geotrellis

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.codahale.jerkson.Json._

case class GeoJson(`type`:String, bbox:Array[Double], geometry:GeoJsonGeometry)

case class GeoJsonGeometry(`type`:String, coordinates:Array[Array[Array[Double]]])

@RunWith(classOf[JUnitRunner])
class SampleTest extends FunSuite {
  test("geojson test") {
  val sample = """{ "type": "Feature",
  "bbox": [-180.0, -90.0, 180.0, 90.0],
  "geometry": {
    "type": "Polygon",
    "coordinates": [[
      [-180.0, 10.0], [20.0, 90.0], [180.0, -5.0], [-30.0, -90.0]
      ]]
    }
  }
  """
  val g = parse[GeoJson](sample)
  println("geometry: " + g.geometry)
  println("coordinates: " + g.geometry.coordinates)
  val c = g.geometry.coordinates
  //println(c(0)(0)(0))
  for (y <- c(0)) {
    //for (y <- x) {
      println(y(0) + ", " + y(1) )
    //  }
    }
  }  
}
