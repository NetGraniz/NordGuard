package dev.nordfjell.guard;

/** Bounded owner-sample trajectory. Packet counts never grant elapsed time. */
final class FlightEnvelope {
    private boolean initialized;
    private double origin, jump, gravity;
    private int age;

    void reset() { initialized=false;age=0; }

    boolean excess(double y,double previousY,double velocity,double acceleration,boolean launch,
                   int ascentAllowance,double margin) {
        if(!Double.isFinite(y) || !Double.isFinite(previousY) || !Double.isFinite(velocity)
                || !Double.isFinite(acceleration) || acceleration<=.001 || velocity<0) {
            reset();return false;
        }
        if(!initialized || launch) {
            initialized=true;origin=previousY;jump=velocity;gravity=acceleration;age=0;
        } else {jump=Math.max(jump,velocity);gravity=Math.min(gravity,acceleration);}
        // Attribute changes may widen the same server-authorized envelope, but
        // cannot restart its clock repeatedly while an airborne player descends.
        ascentAllowance=Math.max(ascentAllowance,(int)Math.ceil(jump/gravity)+6);
        age=Math.min(1_000_000,age+1);
        // Only test the descending branch. A three-sample arrival slack is fixed,
        // not proportional to claimed client ticks or accumulated silent time.
        if(age<=ascentAllowance+3) return false;
        int elapsed=age-3;
        double terminal=-gravity*.98/.02;
        double displacement=(jump-terminal)*(1-Math.pow(.98,elapsed))/.02+terminal*elapsed;
        double ceiling=origin+displacement;
        // A very fast descent cannot purchase an unbounded future hover/slowfall.
        // This only lowers the ceiling; suspicious samples can never raise it.
        double slack=Math.abs(terminal)*3+jump+margin;
        if(ceiling>y+slack) {origin-=ceiling-y-slack;ceiling=y+slack;}
        return y>ceiling+margin;
    }
}
