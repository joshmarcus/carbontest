package carbon

import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.Response
import javax.ws.rs.{GET, POST, Path, DefaultValue, QueryParam}
import javax.ws.rs._
import javax.ws.rs.core.{Response, Context, MediaType, MultivaluedMap}
import geotrellis._
import geotrellis.feature.Polygon
import geotrellis.process._
import geotrellis.raster.op._
import geotrellis.statistics.op._ //stat.TiledPolygonalZonalCount
import geotrellis.raster._
import geotrellis.feature.op.geometry.AsPolygonSet

object Carbon {
  val server = TestServer()

  def init = {
    // Load and cache tileset for future requests.
    var carbonLocation = "/var/geotrellis/carbon_1000"
    val uncachedRaster = Raster.loadUncachedTileSet(carbonLocation, server)
    val raster = Raster.loadTileSet(carbonLocation, server)

    val tileSetRD = raster.data.asInstanceOf[TileArrayRasterData] 
    val tileSums = zonal.Sum.createTileResults(tileSetRD, raster.rasterExtent)

    (raster, uncachedRaster, tileSums)
  }
  
  val (raster, uncachedRaster, tileSums) = init
}


/**
 * Demo rest endpoint
 *
 * Sum carbon values under polygon provided via geojson
 */
@Path("/carbon")
class Carbon {
  
  @POST
  def carbonPost(
    params: MultivaluedMap[String,String]
  ) = {
    var polygonJson = params.getFirst("polygon")
    if (polygonJson == null) {
      Response.serverError().entity("{ \"error\" => 'Carbon server has received an empty request.' }").`type`("application/json").build() 
    } else {
      println("received json: " + polygonJson)
      println("received POST request.")
      carbon(polygonJson, "true")
    }
  }

  @GET
  def carbon(
    @DefaultValue("""
{
   "type": "MultiPolygon",
   "coordinates": [
       [
           [ [102.0, 2.0], [103.0, 2.0], [103.0, 3.0], [102.0, 3.0], [102.0, 2.0] ]
       ],
       [
           [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0] ],
           [ [100.2, 0.2], [100.8, 0.2], [100.8, 0.8], [100.2, 0.8], [100.2, 0.2] ]
       ]
   ]
}
""")


    @QueryParam("polygon")
    polygonJson:String,
    
    @DefaultValue("true")
    @QueryParam("cached")
    cached:String

  ):Any = {
    val start = System.currentTimeMillis()
    val server = Carbon.server

    val raster = if (cached == "true") Carbon.raster else Carbon.uncachedRaster

    var preCount = System.currentTimeMillis

    try {
      val featureOp = io.LoadGeoJsonFeature(polygonJson)
      val polygonSetOp = AsPolygonSet(featureOp)
      val plist = Carbon.server.run(polygonSetOp)
      val count = plist.foldLeft( 0L ) ( (sum:Long, p) => sum + zonalSum(p, raster)) 

      val elapsedTotal = System.currentTimeMillis - preCount
      println ("Request duration: " + elapsedTotal)

      val data = "{ \"carbon_count\": %d, \"elapsed\": %d }".format(count, elapsedTotal)
      Response.ok(data).`type`("application/json").build()
    } catch {
      case e: Exception => { Response.serverError().entity("{ \"error\" => 'Polygon request was invalid.' }").`type`("application/json").build() 
      }
    } 
  }

  def zonalSum(p:Polygon[_], raster:Raster) = {
    val sumOp = zonal.Sum(raster, p, Carbon.tileSums) 
    Carbon.server.run(sumOp)
  }
}
