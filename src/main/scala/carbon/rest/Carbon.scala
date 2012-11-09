package carbon

import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.Response
import javax.ws.rs.{GET, Path, DefaultValue, QueryParam}
import javax.ws.rs.core.{Response, Context}
import geotrellis._
import geotrellis.process._
import geotrellis.raster.op._
import geotrellis.statistics.op.stat.TiledPolygonalZonalCount
import geotrellis.raster._

object Carbon {
  val server = TestServer()

  def init = {
    // Load and cache tileset for future requests.
    val uncachedRaster = Raster.loadUncachedTileSet("/var/trellis/carbon", server)
    val raster = Raster.loadTileSet("/var/trellis/carbon", server)

    val tileSetRD = raster.data.asInstanceOf[TileArrayRasterData] 
    val tileSums = TiledPolygonalZonalCount.createTileSums(tileSetRD, raster.rasterExtent)

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
  @GET
  def carbon(
    @DefaultValue("""
{ "type": "Feature",
  "bbox": [-180.0, -90.0, 180.0, 90.0],
  "geometry": {
    "type": "Polygon",
    "coordinates": [
         [[  67.0000000000001,81.7528915450001],[67.0000000000001,79.5000000050001],[43.0000000000001,79.5000000050001],[43.0000000000001,82.5000000040001],[67.0000000000001,82.5000000040001],[67.0000000000001,81.7528915450001]]
      ]
    }
  }
}
""")


    @QueryParam("polygon")
    polygonJson:String,
    
    @DefaultValue("true")
    @QueryParam("cached")
    cached:String,

    @DefaultValue("4000")
    @QueryParam("limit")
    limit:Int
    
  ):Any = {
    val start = System.currentTimeMillis()
    val server = Carbon.server
    val raster = if (cached == "true") Carbon.raster else Carbon.uncachedRaster

    println("Received request.")
    var preCount = System.currentTimeMillis

    val pOp = io.LoadPolygonGeoJson(polygonJson)
    val p = Carbon.server.run(pOp)

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

    val countOp = TiledPolygonalZonalCount(p, croppedRaster, Carbon.tileSums, limit) 

    val count = server.run(countOp)
    val elapsedTotal = System.currentTimeMillis - preCount
    println ("Request duration: " + elapsedTotal)

    val data = "{ \"carbon_count\": %d, \"elapsed\": %d }".format(count, elapsedTotal)
    Response.ok(data).`type`("application/json").build()
  }
}
