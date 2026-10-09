package dev.nordfjell.guard;

/** Immutable geometry shared between region threads; no Bukkit objects. */
final class Geometry {
    record Point(double x, double y, double z) {
        double distance(Point p) { return Math.sqrt(sq(x-p.x)+sq(y-p.y)+sq(z-p.z)); }
    }
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Box {
            if (!Double.isFinite(minX+minY+minZ+maxX+maxY+maxZ) || minX>maxX || minY>maxY || minZ>maxZ)
                throw new IllegalArgumentException("Invalid geometry");
        }
        Point nearest(Point p) { return new Point(clamp(p.x,minX,maxX),clamp(p.y,minY,maxY),clamp(p.z,minZ,maxZ)); }
        double distance(Point p) { return p.distance(nearest(p)); }
        Box expand(double value) { return new Box(minX-value,minY-value,minZ-value,maxX+value,maxY+value,maxZ+value); }
        Box include(Point p) { return new Box(Math.min(minX,p.x),Math.min(minY,p.y),Math.min(minZ,p.z),
                Math.max(maxX,p.x),Math.max(maxY,p.y),Math.max(maxZ,p.z)); }
        Point center() { return new Point((minX+maxX)/2,(minY+maxY)/2,(minZ+maxZ)/2); }
        Point[] sightPoints(Point eye) {
            // Face corners plus center: a visible corner must not count as a wall hit.
            double e=.001;
            return new Point[]{nearest(eye),center(),new Point(minX+e,minY+e,minZ+e),new Point(maxX-e,minY+e,minZ+e),
                    new Point(minX+e,maxY-e,minZ+e),new Point(maxX-e,maxY-e,minZ+e),
                    new Point(minX+e,minY+e,maxZ-e),new Point(maxX-e,minY+e,maxZ-e),
                    new Point(minX+e,maxY-e,maxZ-e),new Point(maxX-e,maxY-e,maxZ-e)};
        }
        boolean crosses(Point a, Point b) {
            double near=0,far=1;
            for(int i=0;i<3;i++) {
                double start=i==0?a.x:i==1?a.y:a.z;
                double delta=(i==0?b.x:i==1?b.y:b.z)-start;
                double low=i==0?minX:i==1?minY:minZ, high=i==0?maxX:i==1?maxY:maxZ;
                if(Math.abs(delta)<1E-10) { if(start<=low || start>=high) return false; }
                else {
                    double p=(low-start)/delta,q=(high-start)/delta;
                    near=Math.max(near,Math.min(p,q)); far=Math.min(far,Math.max(p,q));
                    if(far-near<1E-7) return false;
                }
            }
            return far>1E-5 && near<1-1E-5 && far-near>1E-5;
        }
    }
    private static double sq(double d) { return d*d; }
    private static double clamp(double d,double low,double high) { return Math.max(low,Math.min(high,d)); }
}
