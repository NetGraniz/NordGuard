package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PredictionSettingsTest {
    @Test void missingSettingsStayDisabled() {assertFalse(PredictionSettings.read(new YamlConfiguration(),true,false).enabled());}
    @Test void predictionRequiresBothDependencies() {
        var yaml=new YamlConfiguration();yaml.set("prediction.enabled",true);
        assertThrows(IllegalArgumentException.class,()->PredictionSettings.read(yaml,true,false));
        assertThrows(IllegalArgumentException.class,()->PredictionSettings.read(yaml,false,true));
        assertTrue(PredictionSettings.read(yaml,true,true).enabled());
    }
    @Test void rejectsNonIntegralUnboundedAndMistypedSettings() {
        for(Object value:new Object[]{0,-1,Double.NaN,20.5,"2000",20001}) {
            var yaml=new YamlConfiguration();yaml.set("prediction.frames-per-second",value);
            assertThrows(IllegalArgumentException.class,()->PredictionSettings.read(yaml,true,true));
        }
        var yaml=new YamlConfiguration();yaml.set("prediction.enabled","true");
        assertThrows(IllegalArgumentException.class,()->PredictionSettings.read(yaml,true,true));
    }
}
