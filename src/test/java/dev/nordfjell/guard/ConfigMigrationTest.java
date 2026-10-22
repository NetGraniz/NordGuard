package dev.nordfjell.guard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {
    @TempDir Path directory;
    private static final String DEFAULTS="""
            schema-version: 1
            checks:
              reach: OBSERVE
              criticals: OBSERVE
            combat:
              wall-confirm-ms: 200
              wall-retention-ms: 1500
            """;
    private ConfigMigration.Result merge(Path file) throws Exception {
        return ConfigMigration.merge(file,new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8)));
    }
    private Path config(String text) throws IOException {
        return Files.writeString(directory.resolve("config.yml"),text,StandardCharsets.UTF_8);
    }
    private YamlConfiguration read(Path file) throws Exception {
        var result=new YamlConfiguration();result.options().parseComments(true);result.load(file.toFile());return result;
    }
    @Test void inheritsMissingCheckAndPreservesExplicitModesUnknownValuesAndComments() throws Exception {
        var file=config("""
                # User configuration
                schema-version: 1
                checks:
                  reach: CORRECT
                  speed: OFF
                custom:
                  label: 'keep this'
                  list: [one, two, three]
                  nested:
                    enabled: false
                    threshold: 7.5
                """);
        var result=merge(file);var updated=read(file);
        assertTrue(result.changed());assertEquals(3,result.addedKeys());
        assertEquals("CORRECT",updated.getString("checks.criticals"));
        assertEquals("CORRECT",updated.getString("checks.reach"));
        assertEquals(Policy.Mode.OFF,Policy.read(updated).modes().get(Check.SPEED));
        assertEquals(java.util.List.of("one","two","three"),updated.getStringList("custom.list"));
        assertEquals("keep this",updated.getString("custom.label"));
        assertFalse(updated.getBoolean("custom.nested.enabled"));
        assertEquals(7.5,updated.getDouble("custom.nested.threshold"));
        assertTrue(Files.readString(file).contains("User configuration"));
    }
    @Test void explicitOffWinsAndBackupIsExactOriginalBytes() throws Exception {
        var file=config("schema-version: 1\r\nchecks:\r\n  reach: CORRECT\r\n  criticals: OFF # explicit\r\n");
        byte[] before=Files.readAllBytes(file);var result=merge(file);
        assertArrayEquals(before,Files.readAllBytes(result.backup()));
        assertEquals(Policy.Mode.OFF,Policy.read(read(file)).modes().get(Check.CRITICALS));
        assertTrue(Files.readString(file).contains("explicit"));
    }
    @Test void defaultNewConfigurationObservesAndSecondStartupIsByteIdentical() throws Exception {
        var file=config(DEFAULTS);byte[] before=Files.readAllBytes(file);
        assertFalse(merge(file).changed());assertArrayEquals(before,Files.readAllBytes(file));
        assertEquals("OBSERVE",read(file).getString("checks.criticals"));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void migratedConfigurationIsIdempotentWithoutAnotherBackup() throws Exception {
        var file=config("schema-version: 1\nchecks:\n  reach: OFF\n");
        merge(file);byte[] first=Files.readAllBytes(file);
        assertFalse(merge(file).changed());assertArrayEquals(first,Files.readAllBytes(file));
        try(var files=Files.list(directory)){assertEquals(2,files.count());}
    }
    @Test void invalidExistingPolicyIsNeverReplacedOrBackedUp() throws Exception {
        var file=config("schema-version: 1\nchecks:\n  reach: WRONG\n");byte[] before=Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class,()->merge(file));assertArrayEquals(before,Files.readAllBytes(file));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void malformedYamlAndConflictingParentRemainUntouched() throws Exception {
        var file=config("schema-version: 1\ncustom: [unterminated\n");byte[] before=Files.readAllBytes(file);
        assertThrows(Exception.class,()->merge(file));assertArrayEquals(before,Files.readAllBytes(file));
        Files.writeString(file,"schema-version: 1\ncombat: [custom, values]\n");before=Files.readAllBytes(file);
        assertThrows(IOException.class,()->merge(file));assertArrayEquals(before,Files.readAllBytes(file));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void refusesLossyNullRewriteInsteadOfRemovingUnknownValue() throws Exception {
        var file=config("schema-version: 1\ncustom-null: null\n");byte[] before=Files.readAllBytes(file);
        assertThrows(IOException.class,()->merge(file));assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void concurrentManualChangeIsNotOverwritten() throws Exception {
        var file=config("schema-version: 1\nchecks:\n  reach: OFF\n");
        byte[] manual="schema-version: 1\nchecks:\n  reach: CORRECT\n# manual change\n".getBytes(StandardCharsets.UTF_8);
        var input=new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8)) {
            @Override public byte[] readAllBytes() {
                try {Files.write(file,manual);} catch(IOException error){throw new java.io.UncheckedIOException(error);}
                return super.readAllBytes();
            }
        };
        assertThrows(IOException.class,()->ConfigMigration.merge(file,input));
        assertArrayEquals(manual,Files.readAllBytes(file));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void existingBackupAndTemporaryNamesAreNeverReused() throws Exception {
        var file=config("schema-version: 1\n");
        Path backup=directory.resolve("config.yml.backup-existing.yml"),temporary=directory.resolve("config.yml.migration-existing.tmp");
        Files.writeString(backup,"existing backup");Files.writeString(temporary,"existing temporary");
        var result=merge(file);
        assertNotEquals(backup,result.backup());assertEquals("existing backup",Files.readString(backup));
        assertEquals("existing temporary",Files.readString(temporary));
    }
    @Test void explicitYamlFalseIsPreservedAsOffAndTrueIsRejected() throws Exception {
        var file=config("schema-version: 1\nchecks:\n  reach: false\n  criticals: false\n");
        merge(file);var updated=read(file);
        assertEquals(Boolean.FALSE,updated.get("checks.reach"));
        assertEquals(Boolean.FALSE,updated.get("checks.criticals"));
        assertEquals(Policy.Mode.OFF,Policy.read(updated).modes().get(Check.CRITICALS));
        Files.writeString(file,"schema-version: 1\nchecks:\n  criticals: true\n");
        byte[] original=Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class,()->merge(file));
        assertArrayEquals(original,Files.readAllBytes(file));
    }
}
