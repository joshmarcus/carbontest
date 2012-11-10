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

object Carbon {
  val server = TestServer()

  def init = {
    // Load and cache tileset for future requests.
    var carbonLocation = "/var/geotrellis/data/arg/carbon"
    val uncachedRaster = Raster.loadUncachedTileSet(carbonLocation, server)
    val raster = Raster.loadTileSet(carbonLocation, server)

    val tileSetRD = raster.data.asInstanceOf[TileArrayRasterData] 
    val tileSums = stat.TiledPolygonalZonalCount.createTileSums(tileSetRD, raster.rasterExtent)

    (raster, uncachedRaster, tileSums)
  }
  
  val (raster, uncachedRaster, tileSums) = init
}


/**
 * Demo rest endpoint for Vizzuality
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
      carbon(polygonJson, "true", "true", "new", 4000)
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
    cached:String,

    @DefaultValue("true")
    @QueryParam("multipolygon")
    multipolygon:String,

    @QueryParam("mode")
    @DefaultValue("old")
    mode:String,

    @DefaultValue("4000")
    @QueryParam("limit")
    limit:Int
    
  ):Any = {
    val start = System.currentTimeMillis()
    val server = Carbon.server
    val useOldOp:Boolean = (mode == "old")

    val raster = if (cached == "true") Carbon.raster else Carbon.uncachedRaster

    var preCount = System.currentTimeMillis

    try {
      val count = if ( multipolygon != "true" ) {
        val pOp = io.LoadPolygonGeoJson(polygonJson)
        val p1 = Carbon.server.run(pOp)
        val count = foo(p1, useOldOp, raster, limit)
        count 
      } else {
        val listPOp =  io.LoadMultiPolygonGeoJson(polygonJson)
        val plist = Carbon.server.run(listPOp)
        val count = plist.foldLeft( 0L ) ( (sum:Long, p) => sum + foo(p, useOldOp, raster, limit) ) 
        //val count = foo(plist(0), useOldOp, raster, limit)
        count
      }

      val elapsedTotal = System.currentTimeMillis - preCount
      println ("Request duration: " + elapsedTotal)

      val data = "{ \"carbon_count\": %d, \"elapsed\": %d }".format(count, elapsedTotal)
      Response.ok(data).`type`("application/json").build()
    } catch {
      case e: Exception => { Response.serverError().entity("{ \"error\" => 'Polygon request was invalid.' }").`type`("application/json").build() 
      }
    } 
  }

  def foo(p:Polygon[Unit], useOldOp:Boolean, raster:Raster, limit:Int) = {
    val server = Carbon.server
    val polygonEnvelope = p.geom.getEnvelopeInternal
     
    val newExtentOp = extent.CropRasterExtent(
      extent.GetRasterExtent(raster), 
      polygonEnvelope.getMinX(),
      polygonEnvelope.getMinY(),
      polygonEnvelope.getMaxX(),
      polygonEnvelope.getMaxY()
    )

    val pExtent = server.run(newExtentOp)
    val croppedRaster = CroppedRaster(raster,pExtent.extent)

    val countOp = if (useOldOp) {
      println("Using old zonal summary operation.")
      stat.TiledPolygonalZonalCount(p, croppedRaster, Carbon.tileSums, limit) 
    } else {
      println("Using new zonal summary operation.")
      zonal.TiledPolygonalZonalSum(p, croppedRaster, Carbon.tileSums, limit) 
    }
 
    val count = server.run(countOp)
    count
  }
}
