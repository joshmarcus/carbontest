package carbon

import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.Response
import javax.ws.rs.{GET, POST, Path, DefaultValue, QueryParam}
import javax.ws.rs._
import javax.ws.rs.core.{Response, Context, MediaType}
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
    val uncachedRaster = Raster.loadUncachedTileSet("/var/trellis/carbon", server)
    val raster = Raster.loadTileSet("/var/trellis/carbon", server)

    val tileSetRD = raster.data.asInstanceOf[TileArrayRasterData] 
    val tileSums = stat.TiledPolygonalZonalCount.createTileSums(tileSetRD, raster.rasterExtent)

    (raster, uncachedRaster, tileSums)
  }
  
  val (raster, uncachedRaster, tileSums) = init
}


/*
[[[46.8987059660001,-72.9305432739999],[46.8603568440001,-72.9026982369999],[46.8701684350001,-72.8718149719999],[46.8582367130001,-72.8503932059999],[46.8540188550001,-72.8106433749999],[46.8601017120001,-72.7931613979999],[46.8443175970001,-72.7652966349999],[46.7743482080001,-72.8005041699999],[46.7507046780001,-72.7945144059999],[46.7277581980001,-72.7661873269999],[46.7387796160001,-72.7890890649999],[46.7275732720001,-72.8051887809999],[46.7217330760001,-72.7965863709999],[46.6993313750001,-72.8327215129999],[46.6947933020001,-72.8262883529999],[46.6853179800001,-72.8403467879999],[46.6788833820001,-72.8323560439999],[46.6696854910001,-72.8461672849999],[46.6610962430001,-72.8332544419999],[46.6510625530001,-72.8436434819999],[46.6464987870001,-72.8520730299999],[46.6555432620001,-72.8661898859999],[46.6639057520001,-72.8591963619999],[46.6731795260001,-72.8782019609999],[46.7239680570001,-72.9090768969999],[46.7336678200001,-72.9270037009999],[46.7455925620001,-72.9938159559999],[46.7363134210001,-73.0115222509999],[46.6831655030001,-72.9869977249999],[46.6615663270001,-72.9900152899999],[46.6496921660001,-72.9640384449999],[46.6489143340001,-72.9993395169999],[46.6530826500001,-73.0320505839999],[46.7067260780001,-73.0641309569999],[46.7041270210001,-73.1068509759999],[46.7508603320001,-73.1789583839999],[46.7645231160001,-73.1693505189999],[46.7705906670001,-73.1437543979999],[46.7867733000001,-73.1321568659999],[46.8329199780001,-73.1324262069999],[46.8283279460001,-73.1272984199999],[46.8357462580001,-73.1099951379999],[46.8507769270001,-73.1067260969999],[46.8662109760001,-73.0853116489999],[46.8863109240001,-73.0785434179999],[46.9020384780001,-73.0438867939999],[46.9176470430001,-73.0394216629999],[46.9186829380001,-73.0178932989999],[46.9088636110001,-73.0083853339999],[46.8988013160001,-72.9691029289999],[46.8987059660001,-72.9305432739999]]]
*/

/**
 * Demo rest endpoint for Vizzuality
 *
 * Sum carbon values under polygon provided via geojson
 */
@Path("/carbon")
class Carbon {
  
  @POST
  def carbonPost(
    polygonJson:String
  ) = {
    if (polygonJson == null) {
      Response.serverError().entity("{ \"error\" => 'Carbon server has received an empty request.' }").`type`("application/json").build() 
    } else {
    //println("received json: " + polygonJson)
    //println("received POST request.")
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
