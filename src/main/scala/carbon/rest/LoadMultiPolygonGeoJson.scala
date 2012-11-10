package geotrellis.io

import net.liftweb.json

import geotrellis._
import geotrellis.feature.Polygon

case class MultiPolygonGeoJson(`type`:String, geometry:MultiPolygonGeoJsonGeometry)
case class MultiPolygonGeoJsonGeometry(`type`:String, coordinates:Array[Array[Array[Array[Double]]]])

object LoadMultiPolygonGeoJson {
  /**
   * Load geojson feature without any associated data.
   */
  def apply(json:String):LoadMultiPolygonGeoJson[Unit] = new LoadMultiPolygonGeoJson(json, ())
}

case class LoadMultiPolygonGeoJson[D] (geojson:Op[String], data:Op[D]) extends Op2(geojson,data) ({
  (geojson,data) => {
 
    if (geojson == null) {
      throw new Exception("received null geojson string")
    } 
    val l = (json.parse(geojson) \\ "coordinates").values
    val values = l.asInstanceOf[List[List[List[List[Double]]]]]
    //val polygonList = for( polygonCoords <- polygons ) yield {
    //  Polygon(polygonCoords, data)
    //} 
    val polygonList = for (polygonCoords <- values) yield {
      Polygon(polygonCoords, data)
    }
    Result(polygonList) 
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
