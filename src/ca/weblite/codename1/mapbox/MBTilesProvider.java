/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.codename1.mapbox;

import ca.weblite.codename1.io.tar.TarEntry;
import ca.weblite.codename1.io.tar.TarInputStream;
import com.codename1.components.StorageImage;

import com.codename1.io.FileSystemStorage;
import com.codename1.io.JSONParser;
import com.codename1.io.Log;
import com.codename1.io.Storage;
import com.codename1.io.gzip.GZIPInputStream;
import com.codename1.maps.BoundingBox;
import com.codename1.maps.Coord;
import com.codename1.maps.Mercator;
import com.codename1.maps.Tile;
import com.codename1.maps.providers.TiledProvider;
import com.codename1.ui.Display;
import com.codename1.ui.Image;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.geom.Point;
import com.codename1.util.MathUtil;
import com.codename1.util.StringUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;




/**
 * 
 * 
 * @author shannah
 */
public class MBTilesProvider extends TiledProvider {
   
    private Map<String,String> metadata = null;

    private String name = null;
    private Image defaultImage = null;
    private BoundingBox bounds = null;
    private Point _tileNo = null;
    private Map<String,Image> cache = new HashMap<String,Image>();
    
    
    
    private static String nameFromPath(String path){
        String dbName = path;
        if ( dbName.indexOf("/") != -1 ){
            int p = dbName.lastIndexOf("/");
            dbName = dbName.substring(p+1);
        }
        return dbName;
    }
    
    public static MBTilesProvider createFromResource(String path) throws IOException {
        String dbName = nameFromPath(path);
        
        if ( dbName.length() == 0 ){
            throw new IOException("No Database Name found.");
        }
        
        InputStream is = null;
        try {
            Log.p("Creating resource stream for "+path);
            is = Display.getInstance().getResourceAsStream(null, path);
            Log.p("Resource stream created");
            return create(dbName, is);
        } finally {
            try {
                is.close();
            } catch (Throwable t){}
        }
    }
    
    public static MBTilesProvider createFromFile(String path) throws IOException {
        FileSystemStorage fs = FileSystemStorage.getInstance();
        if (!fs.exists(path) ){
            throw new IOException("Mbtiles file doesn't exist: "+path);
        }
        
        String dbName = nameFromPath(path);
        
        if ( dbName.length() == 0 ){
            throw new IOException("No Database Name found.");
        }
        
        InputStream is = null;
        try {
            is = fs.openInputStream(path);
            return create(dbName, is);
        } finally {
            try {
                is.close();
            } catch (Throwable t){}
        }
    }
    
    private static String sanitizeCacheId(String url){
        String cacheId = url;
            cacheId = StringUtil.replaceAll(cacheId, "\\", "_");
            cacheId = StringUtil.replaceAll(cacheId, "/", "_");
            cacheId = StringUtil.replaceAll(cacheId, ".", "_");
            cacheId = StringUtil.replaceAll(cacheId, "?", "_");
            cacheId = StringUtil.replaceAll(cacheId, "&", "_");
            return cacheId;
    }
    
    public static MBTilesProvider create(String name, InputStream is) throws IOException {
        
        //FileSystemStorage fs = FileSystemStorage.getInstance();
        //Log.p("Creating tar input stream");
        TarInputStream tis = new TarInputStream(new GZIPInputStream(is));
        //Log.p("Tar stream created");
        TarEntry entry = null;
        byte[] buf = new byte[8192];
        int count = -1;
        //Log.p("About to loop through entries");
        while ( (entry = tis.getNextEntry()) != null ){
            //Log.p("In entry "+entry);
            OutputStream sos = null;
            String cacheId = sanitizeCacheId("cn1tiles_"+name+":"+entry.getName());
            
            
            try {
                //Log.p("Writing cache id "+cacheId);
                if ( cacheId.endsWith("_png")){
                    StorageImage.create(cacheId, tis, -1, -1);
                } else {
                    sos = Storage.getInstance().createOutputStream(cacheId);
                    while ( (count = tis.read(buf)) != -1 ){
                        sos.write(buf, 0, count);
                    }
                    sos.flush();
                    sos.close();
                }
            } finally {
                try {
                    sos.close();
                } catch ( Exception ex){}
            }
        }
        
        //Log.p("About to enter mbtiles constructor");
        return new MBTilesProvider(name);
        
    }
    
    
    public static boolean isLoaded(String dbName) throws IOException {
        
        dbName = nameFromPath(dbName);
        MBTilesProvider p = new MBTilesProvider(dbName);
        return Storage.getInstance().exists(sanitizeCacheId(p.getMetadataKey()));
        
    }
    
    
    public MBTilesProvider(String dbName) throws IOException {
        super(dbName, new Mercator(), new Dimension(256,256));
        this.name = dbName;
        
        
        
        
        
    }
    
    
    
    private String getStorageKeyPrefix(){
        return "cn1tiles_"+this.name+":";
    }
    
    
    static InputStream getFromStorage(String key) throws IOException{
        return Storage.getInstance().createInputStream(sanitizeCacheId(key));
    }
    
    static boolean existsInStorage(String key) throws IOException {
        return Storage.getInstance().exists(sanitizeCacheId(key));
    }
    
    private String getMetadataKey(){
        return getStorageKeyPrefix()+"metadata.json";
    }
    
    private String getTileKey(int row, int col, int zoom){
        StringBuilder sb = new StringBuilder();
        sb.append(getStorageKeyPrefix())
                .append("tiles")
                .append("_")
                .append(zoom).append("_")
                .append(col).append("_")
                .append(row).append(".png");
                
        return sb.toString();
    }
    
    
    
    private int xFromLongitude(double lng, int zoom){
        int x = (int)Math.floor((lng+180.0)/360.0 * MathUtil.pow(2.0, zoom));

        return x;
    }
    
    private int yFromLatitude(double lat, int zoom){
        int y = (int)(Math.floor((1.0-MathUtil.log(Math.tan(lat * Math.PI/180.0) + 1.0 / Math.cos(lat * Math.PI/180.0)) / Math.PI) / 2.0 * MathUtil.pow(2.0, zoom)));
        y = (int)MathUtil.pow(2.0, zoom)-1-y;
        return y;
    }
    
    private static Point getTileNumber(final double lat, final double lon, final int zoom) {
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor((1 - MathUtil.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        
        if (xtile < 0) {
            xtile = 0;
        }
        if (xtile >= (1 << zoom)) {
            xtile = ((1 << zoom) - 1);
        }
        if (ytile < 0) {
            ytile = 0;
        }
        if (ytile >= (1 << zoom)) {
            ytile = ((1 << zoom) - 1);
        }
        ytile = (int)( 1 << zoom) - ytile;
        return new Point(xtile, ytile);
        //return ("" + zoom + "/" + xtile + "/" + ytile);
    }

    
    /**
     * @inheritDoc
     */
    @Override
    public BoundingBox bboxFor(Coord position, int zoomLevel) {
        _zoomLevel = zoomLevel;
        
        Coord pos = projection().toWGS84(position);
        _tileNo = getTileNumber(pos.getLatitude(), pos.getLongitude(), _zoomLevel);
        
        double south = tile2lat(_tileNo.getY(), _zoomLevel);
        double north = tile2lat(_tileNo.getY()+1, _zoomLevel);
        double west = tile2lon(_tileNo.getX(), _zoomLevel);
        double east = tile2lon(_tileNo.getX()+1, _zoomLevel);
        
        Coord start = new Coord(south, west);
        Coord end = new Coord(north, east);
        
        BoundingBox out = new BoundingBox(start, end);
        //Log.p("BBOX is "+out);
        out = projection().fromWGS84(out);
        //Log.p("Projected BBOX is "+out);
        //Coord scaled = this.scale(zoomLevel);
        //Log.p("Scale is "+scaled);
        //Coord sw = new Coord(out.getSouthWest().getLatitude()*scaled.getLatitude(), out.getSouthWest().getLongitude()*scaled.getLongitude());
        //Log.p("Scaled sw "+sw);
        //Log.p("TileNo "+_tileNo);
        return out;
        
    }

    
    
    
    private static double tile2lon(int x, int z) {
        return x / MathUtil.pow(2.0, z) * 360.0 - 180;
    }

    private static double tile2lat(int y, int z) {
        y = (int)(1 << z)-y;
        double n = Math.PI - (2.0 * Math.PI * y) / MathUtil.pow(2.0, z);
        return Math.toDegrees(MathUtil.atan(sinh(n)));
    }
    
    private static double sinh(double x) {
        return (MathUtil.exp(x)-MathUtil.exp(-x))/2.0;
    }
    

    /**
     * @inheritDoc
     */
    @Override
    public Tile tileFor(BoundingBox bbox) { 
        Point tileNum = _tileNo;
        int row = tileNum.getY();//this.yFromLatitude(lat, _zoomLevel);
        int col = tileNum.getX();//this.xFromLongitude(lng, _zoomLevel);
        String storageKey = getTileKey(row, col, _zoomLevel);
        Image img = cache.get(sanitizeCacheId(storageKey));
        if ( img == null ){
            if ( Storage.getInstance().exists(sanitizeCacheId(storageKey))){
                
                img = StorageImage.create(sanitizeCacheId(storageKey), -1, -1);
            } else {
                img = defaultImage();
                
            }
            cache.put(sanitizeCacheId(storageKey), img);
        }
        
        Tile tile = new Tile(tileSize(), bbox, img);
        return tile;

    }
    
    
    Image defaultImage(){
        if ( defaultImage == null ){
            defaultImage = Image.createImage(256, 256);
            defaultImage.lock();
        }
        return defaultImage;
    }
    
    
 
  

    
    public Map<String,String> metadata(){
        if ( metadata == null ){
            InputStreamReader r = null;
            try {
                metadata = new HashMap<String,String>();
                JSONParser p = new JSONParser();
                //Log.p("Metadata key is "+this.getMetadataKey());
                r = new InputStreamReader(getFromStorage(this.getMetadataKey()));
                Hashtable t = p.parse(r);
                Enumeration en = t.keys();
                while (en.hasMoreElements() ){
                    String nex = (String)en.nextElement();
                    metadata.put(nex, (String)t.get(nex));
                }
                
                
            } catch (IOException ex){
                Log.e(ex);
            } finally {
                try {
                    r.close();
                } catch ( Exception ex){}
            }
                
        }
        return metadata;
    }
    

    @Override
    public int maxZoomLevel() {
        return Integer.parseInt(metadata().get("maxzoom"));
    }

    @Override
    public int minZoomLevel() {
        return Integer.parseInt(metadata().get("minzoom"));
    }
    
    

    @Override
    public String attribution() {
        return metadata().get("attribution");
    }
    
    public Coord center(){
        String pos = metadata().get("center");
        int commaPos = pos.indexOf(",");
        String x = pos.substring(0, commaPos);
        String y = pos.substring(commaPos+1, pos.indexOf(",", commaPos+1));
        Coord out = new Coord(Double.parseDouble(y), Double.parseDouble(x));
        return out;
    }
    
    public BoundingBox bounds(){
        if ( this.bounds == null ){
            String sBounds = metadata().get("bounds");
            double[] bounds = new double[4];
            int i = 0;
            int pos = 0;
            while ( i < 4 ){
                int commaPos = sBounds.indexOf(",", pos);
                if ( commaPos == -1 ){
                    commaPos = sBounds.length();
                }
                if ( commaPos <= pos ){
                    break;
                }
                String nex = sBounds.substring(pos, commaPos);
                bounds[i] = Double.parseDouble(nex);
                i++;
                pos = commaPos+1;

            }
            //Log.p("Bounds "+bounds[0]+", "+bounds[1]+", "+bounds[2]+", "+bounds[3]);
            this.bounds = new BoundingBox(new Coord(bounds[1], bounds[0]), new Coord(bounds[3], bounds[2]));
        }
       // Log.p(this.bounds.toString());
        return this.bounds;
    }
    
    
    
    
}
