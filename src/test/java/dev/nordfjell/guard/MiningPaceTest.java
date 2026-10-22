package dev.nordfjell.guard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningPaceTest {
    private static final long MS=1_000_000L;
    private static final MiningLimits LIMITS=MiningLimits.defaults();
    @Test void ordinaryDirtAndWoodenPickStoneIntervalsRemainAllowed() {
        for(long work:new long[]{700*MS,1100*MS}) {
            var pace=new MiningPace();long now=0;
            for(int i=0;i<100;i++) {assertTrue(pace.allows(now,work,LIMITS));pace.commit(now,work,LIMITS);now+=work+300*MS;}
        }
    }
    @Test void boundedDelayJitterDoesNotBecomeRepeatedSpeedEvidence() {
        var pace=new MiningPace();long now=0;int[] jitter={0,120,0,-120,0,80,0,-80};
        for(int i=0;i<200;i++) {
            assertTrue(pace.allows(now,700*MS,LIMITS));pace.commit(now,700*MS,LIMITS);
            now+=(1000+jitter[i%jitter.length])*MS;
        }
    }
    @Test void repeatedRealFastBreakCadenceConsumesSlackInsteadOfReceivingItPerBlock() {
        for(long[] pair:new long[][]{{700*MS,600*MS},{1100*MS,900*MS}}) {
            var pace=new MiningPace();long now=0;int rejected=0;
            for(int i=0;i<30;i++) {
                if(pace.allows(now,pair[0],LIMITS))pace.commit(now,pair[0],LIMITS);else rejected++;
                now+=pair[1];
            }
            assertTrue(rejected>=5,"Repeated faster-than-full-work mining was not constrained");
        }
    }
    @Test void rejectedRetryDoesNotConsumeWorkOrForgetThePreviousAcceptedBlock() {
        var pace=new MiningPace();pace.commit(0,700*MS,LIMITS);
        assertFalse(pace.allows(600*MS,700*MS,LIMITS));
        assertFalse(pace.allows(650*MS,700*MS,LIMITS));
        assertTrue(pace.allows(750*MS,700*MS,LIMITS));pace.commit(750*MS,700*MS,LIMITS);
        assertFalse(pace.allows(1300*MS,700*MS,LIMITS));
    }
    @Test void instantMiningStaysAllowedWithoutForgivingExistingWorkDebt() {
        var pace=new MiningPace();pace.commit(0,700*MS,LIMITS);pace.commit(750*MS,700*MS,LIMITS);
        assertTrue(pace.allows(800*MS,0,LIMITS));pace.commit(800*MS,0,LIMITS);
        assertFalse(pace.allows(850*MS,700*MS,LIMITS));
        for(int i=1;i<=100;i++) {long now=(850+i*50)*MS;assertTrue(pace.allows(now,0,LIMITS));pace.commit(now,0,LIMITS);}
    }
    @Test void longIdleRefillsOnlyBoundedReserve() {
        var pace=new MiningPace();pace.commit(0,700*MS,LIMITS);pace.commit(100_000*MS,700*MS,LIMITS);
        assertFalse(pace.allows(100_100*MS,700*MS,LIMITS));
    }
    @Test void observeDebtRepaymentIsNotFreshAcceleratedMining() {
        var pace=new MiningPace();pace.commit(0,700*MS,LIMITS);
        for(int i=1;i<=100;i++)pace.commit(i*100*MS,700*MS,LIMITS);
        assertTrue(pace.allows(11_000*MS,700*MS,LIMITS));
    }
    @Test void backwardTimeDefersAndCommitStartsANewClock() {
        var pace=new MiningPace();pace.commit(1000*MS,700*MS,LIMITS);
        assertTrue(pace.allows(0,700*MS,LIMITS));pace.commit(0,700*MS,LIMITS);
        assertTrue(pace.allows(1000*MS,700*MS,LIMITS));
    }
    @Test void fullWorkUsesTheFastestObservedNativeSpeedAndAllowsAuthorizedInstant() {
        var dirt=new MiningProgress(0.06666667014360428,false,0);assertEquals(700*MS,dirt.fullWorkNanos());
        var stone=new MiningProgress(0.04444444552063942,false,0);assertEquals(1100*MS,stone.fullWorkNanos());
        stone.advance(.1);assertEquals(450*MS,stone.fullWorkNanos());
        stone.advance(1);assertEquals(0,stone.fullWorkNanos());
        assertEquals(0,new MiningProgress(.01,true,0).fullWorkNanos());
        assertEquals(0,new MiningProgress(Double.NaN,false,0).fullWorkNanos());
        assertTrue(new MiningProgress(Double.MIN_VALUE,false,0).fullWorkNanos()<=60_000*MS);
    }
    @Test void configurationNumbersAreValidatedAndExplicitValuesWin() {
        var yaml=new YamlConfiguration();assertEquals(LIMITS,MiningLimits.read(yaml));
        yaml.set("mining.inter-block-delay-ticks",0);yaml.set("mining.minimum-progress",1.0);
        var changed=MiningLimits.read(yaml);assertEquals(0,changed.pauseTicks());assertEquals(1,changed.minimumProgress());
        for(Object bad:new Object[]{true,"five",5.5,-1,100}) {
            yaml.set("mining.burst-ticks",bad);assertThrows(IllegalArgumentException.class,()->MiningLimits.read(yaml));
        }
        yaml.set("mining.burst-ticks",5);yaml.set("mining.minimum-progress",Double.NaN);
        assertThrows(IllegalArgumentException.class,()->MiningLimits.read(yaml));
    }
}
