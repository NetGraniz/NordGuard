package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PacketWorkloadTest {
    @Test void sixHundredIndependentHistoriesStayBounded() {
        var inboxes = new PacketInbox[600]; var timelines = new PacketTimeline[600];
        for (int i=0; i<600; i++) { inboxes[i]=new PacketInbox(); timelines[i]=new PacketTimeline(); }
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        var allocations = bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported() ? b : null;
        if (allocations != null) allocations.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().threadId();
        long allocated = allocations == null ? -1 : allocations.getThreadAllocatedBytes(id);
        long start = System.nanoTime();
        for (int tick=1; tick<=1200; tick++) for (int player=0; player<600; player++) {
            long nano = tick*50_000_000L;
            PacketInbox q = inboxes[player];
            q.event(NativePackets.INPUT,nano,0,65,0,0,0,0,0);
            q.event(NativePackets.MOVE,nano,0,5,tick*.2,80,player,0,0);
            q.event(NativePackets.TICK_END,nano,0,0,0,0,0,0,0);
            timelines[player].drain(q,nano);
        }
        long elapsed = System.nanoTime()-start;
        long bytes = allocations == null ? -1 : allocations.getThreadAllocatedBytes(id)-allocated;
        for (int player=0; player<600; player++) {
            assertEquals(3600,timelines[player].events()); assertEquals(1200,timelines[player].ticks());
            assertEquals(64,timelines[player].historySize()); assertEquals(0,inboxes[player].dropped());
            assertEquals(0,timelines[player].timerExcess());
        }
        System.out.println("NORD_PACKET_WORKLOAD events=2160000 sessions=600 elapsed_ms="+elapsed/1_000_000
                +" allocated_bytes_after_construction="+bytes+"; excludes Netty, native decode, world, schedulers and players");
    }
}
