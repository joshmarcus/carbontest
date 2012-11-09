package geotrellis.io

import com.codahale.jerkson.Json._

import geotrellis._
import geotrellis.feature.Polygon

case class GeoJson(`type`:String, geometry:GeoJsonGeometry)
case class GeoJsonGeometry(`type`:String, coordinates:Array[Array[Array[Double]]])

object LoadPolygonGeoJson {
  /**
   * Load geojson feature without any associated data.
   */
  def apply(json:String):LoadPolygonGeoJson[Unit] = new LoadPolygonGeoJson(json, ())
}

case class LoadPolygonGeoJson[D] (json:Op[String], data:Op[D]) extends Op2(json,data) ({
  (json, data) => {
    val g = parse[GeoJson](json)
    val coords = g.geometry.coordinates
    Result(Polygon(coords, data))    
  }
})

/*
{ "type": "Feature",
  "bbox": [-180.0, -90.0, 180.0, 90.0],
  "geometry": {
    "type": "Polygon",
    "coordinates": [[
      [-180.0, 10.0], [20.0, 90.0], [180.0, -5.0], [-30.0, -90.0]
      ]]
    }
  }
}
*/
