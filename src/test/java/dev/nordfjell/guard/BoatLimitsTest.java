package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BoatLimitsTest {
    @Test void malformedNumericSettingsDoNotSilentlyUseDefaults() {
        for(Object value:new Object[]{"six",true,java.util.List.of(6),6.5}) {
            var yaml=new YamlConfiguration();yaml.set("vehicle.violation-buffer",value);
            assertThrows(IllegalArgumentException.class,()->BoatLimits.read(yaml));
        }
        for(Object value:new Object[]{"margin",false,java.util.List.of(.35)}) {
            var yaml=new YamlConfiguration();yaml.set("vehicle.rise-margin",value);
            assertThrows(IllegalArgumentException.class,()->BoatLimits.read(yaml));
        }
    }
    @Test void unquotedYamlOffRemainsOffIncludingInheritedModes() throws Exception {
        var yaml=new YamlConfiguration();
        yaml.loadFromString("schema-version: 1\nchecks:\n  flight: OFF\n  reach: OFF\n");
        var policy=Policy.read(yaml);
        assertEquals(Policy.Mode.OFF,policy.modes().get(Check.FLIGHT));
        assertEquals(Policy.Mode.OFF,policy.modes().get(Check.BOATFLIGHT));
        assertEquals(Policy.Mode.OFF,policy.modes().get(Check.CRITICALS));
        yaml.set("checks.boatflight","observe");
        assertEquals(Policy.Mode.OBSERVE,Policy.read(yaml).modes().get(Check.BOATFLIGHT));
        yaml.set("checks.boatflight",true);
        assertThrows(IllegalArgumentException.class,()->Policy.read(yaml));
    }
    @Test void absentSettingsUseConservativeDefaults() {
        assertEquals(BoatLimits.defaults(),BoatLimits.read(new YamlConfiguration()));
        assertTrue(BoatLimits.defaults().bubbleGraceTicks()>=120);
        assertTrue(BoatLimits.defaults().maxProbeBlocks()<=128);
    }
    @Test void explicitVehicleValuesAreReadWithoutCheckModes() {
        var yaml=new YamlConfiguration();
        yaml.set("vehicle.violation-buffer",9);yaml.set("vehicle.rise-margin",.5);
        yaml.set("vehicle.max-probe-blocks",81);yaml.set("vehicle.max-setback-distance",16);
        var limits=BoatLimits.read(yaml);
        assertEquals(9,limits.buffer());assertEquals(.5,limits.riseMargin());
        assertEquals(81,limits.maxProbeBlocks());assertEquals(16,limits.maxSetbackDistance());
    }
    @Test void invalidIntegerSettingsRejectRatherThanPublishPartialLimits() {
        String[] keys={"violation-buffer","air-grace-ticks","bubble-grace-ticks","max-sample-gap-ms",
                "max-probe-blocks","correction-cooldown-ticks","anchor-max-age-seconds"};
        for(String key:keys)for(int value:new int[]{-1,0,Integer.MAX_VALUE}) {
            var yaml=new YamlConfiguration();yaml.set("vehicle."+key,value);
            assertThrows(IllegalArgumentException.class,()->BoatLimits.read(yaml),key+"="+value);
        }
    }
    @Test void finiteMarginsAndDistancesAreMandatory() {
        for(String key:new String[]{"rise-margin","max-setback-distance"})
            for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1,0,1000}) {
                var yaml=new YamlConfiguration();yaml.set("vehicle."+key,value);
                assertThrows(IllegalArgumentException.class,()->BoatLimits.read(yaml),key+"="+value);
            }
    }
    @Test void bubbleGraceCannotBeShorterThanTheNativeLaunchAllowance() {
        var yaml=new YamlConfiguration();yaml.set("vehicle.bubble-grace-ticks",119);
        assertThrows(IllegalArgumentException.class,()->BoatLimits.read(yaml));
    }
}
