package dev.nordfjell.guard;

/** Fixed-size owner-sample state. No entity/world access and no client tick-count clock. */
final class BoatModel {
    private static final double GRAVITY=.04, MAX_INITIAL_RISE=.12;
    private Frame last;
    private int airAge,bubbleGrace;
    private double originY,evidence;
    record Frame(long nano,double x,double y,double z,boolean supported,boolean water,
                 boolean bubble,boolean exempt,boolean freshMovement,double gravity) {}
    record Result(boolean violation,boolean clean,int evidence) {}

    void reset() {last=null;airAge=bubbleGrace=0;originY=evidence=0;}

    /** A transport pause creates neither hover evidence nor a new exemption allowance. */
    void transportIdleTick() {}

    Result accept(Frame next,BoatLimits limits) {
        if(!Double.isFinite(next.x)||!Double.isFinite(next.y)||!Double.isFinite(next.z)
                ||!Double.isFinite(next.gravity)||Math.abs(next.gravity-GRAVITY)>1E-6||next.exempt) {
            reset();return new Result(false,false,0);
        }
        boolean moved=last!=null&&(Math.abs(next.x-last.x)>1E-8||Math.abs(next.y-last.y)>1E-8||Math.abs(next.z-last.z)>1E-8);
        if(last!=null && (next.nano<last.nano || next.nano-last.nano>limits.maxGapMillis()*1_000_000L)) {
            int remaining=bubbleGrace;reset();bubbleGrace=remaining;
        }
        if(next.bubble)bubbleGrace=limits.bubbleGraceTicks();
        else if(bubbleGrace>0&&(next.freshMovement||moved))bubbleGrace--;
        if(last==null) {
            last=next;originY=next.y;
            return new Result(false,false,0);
        }
        if(next.supported||next.water) {
            boolean clean=(last.supported||last.water)&&bubbleGrace==0&&!next.bubble;
            airAge=0;originY=next.y;evidence=0;last=next;
            return new Result(false,clean,0);
        }
        if(bubbleGrace>0||next.bubble) {
            // The native bubble launch can be 2.7 blocks/tick with player passengers.
            // This bounded grace is not an upward impulse inferred from untrusted travel.
            airAge=0;originY=next.y;evidence=0;last=next;
            return new Result(false,false,0);
        }
        if(!next.freshMovement&&!moved) {
            last=next;return new Result(false,false,(int)evidence);
        }
        if(last.supported||last.water) {airAge=0;originY=last.y;}
        airAge=Math.min(10_000,airAge+1);
        // A fixed owner-time grace absorbs coalesced delivery and water-exit motion.
        // The IN_AIR native boat has gravity .04 and vertical air drag 1, not player .98.
        int ticks=Math.max(0,airAge-limits.airGraceTicks());
        double upper=originY+MAX_INITIAL_RISE*ticks-GRAVITY*ticks*(ticks+1)*.5+limits.riseMargin();
        boolean suspicious=airAge>limits.airGraceTicks()&&next.y>upper;
        evidence=suspicious?Math.min(limits.buffer()+2,evidence+1):Math.max(0,evidence-.25);
        last=next;
        return new Result(suspicious&&evidence>=limits.buffer(),false,(int)evidence);
    }
}
