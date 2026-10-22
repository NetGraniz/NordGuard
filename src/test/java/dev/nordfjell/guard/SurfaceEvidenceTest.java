package dev.nordfjell.guard;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceEvidenceTest {
    @Test void sustainedSurfaceRequiresWholeWindow(){var e=new SurfaceEvidence();
        for(int i=1;i<12;i++)assertFalse(e.accept(true,80,i*50_000_000L,12,250_000_000L));
        assertTrue(e.accept(true,80,600_000_000L,12,250_000_000L));}
    @Test void normalSinkingDoesNotCompleteSurfaceWindow(){var e=new SurfaceEvidence();
        for(int i=1;i<200;i++)assertFalse(e.accept(true,80-i*.02,i*50_000_000L,12,250_000_000L));}
    @Test void unknownTerrainOrBootsResetEvidence(){var e=new SurfaceEvidence();
        for(int i=1;i<12;i++)e.accept(true,80,i*50_000_000L,12,250_000_000L);
        assertFalse(e.accept(false,80,600_000_000L,12,250_000_000L));
        assertFalse(e.accept(true,80,650_000_000L,12,250_000_000L));}
    @Test void longTransportGapCannotCompleteEvidence(){var e=new SurfaceEvidence();
        for(int i=1;i<12;i++)e.accept(true,80,i*50_000_000L,12,250_000_000L);
        assertFalse(e.accept(true,80,2_000_000_000L,12,250_000_000L));}
    @Test void rejectsInvalidAndOverwideTolerances(){assertThrows(IllegalArgumentException.class,()->new MovementLimits(7,6,12,.5));
        assertThrows(IllegalArgumentException.class,()->new MovementLimits(6,9,12,.5));
        assertThrows(IllegalArgumentException.class,()->new MovementLimits(6,6,2,.5));
        assertThrows(IllegalArgumentException.class,()->new MovementLimits(6,6,12,Double.NaN));}
    @Test void missingSnowModeInheritsWaterAndStrictNumericSettings(){var c=new MemoryConfiguration();c.set("schema-version",1);
        c.set("checks.waterwalk","CORRECT");assertEquals(Policy.Mode.CORRECT,Policy.read(c).modes().get(Check.POWDERSNOW));
        c.set("checks.powdersnow","OFF");assertEquals(Policy.Mode.OFF,Policy.read(c).modes().get(Check.POWDERSNOW));
        c.set("movement.powder-snow-samples",12.5);assertThrows(IllegalArgumentException.class,()->Policy.read(c));}
}
