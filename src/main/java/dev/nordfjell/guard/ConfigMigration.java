package dev.nordfjell.guard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Startup-only additive migration. Never writes defaults into an invalid existing policy. */
final class ConfigMigration {
    record Result(int addedKeys, Path backup) {
        boolean changed() { return addedKeys != 0; }
    }

    static Result merge(Path config, InputStream defaults) throws IOException, InvalidConfigurationException {
        Objects.requireNonNull(defaults);
        Path target=config.toAbsolutePath().normalize();
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Configuration must be an existing regular file");
        byte[] original=Files.readAllBytes(target);
        String originalText=decode(original);
        YamlConfiguration old=parse(originalText), bundled=parse(decode(defaults.readAllBytes()));
        Policy effective=Policy.read(old);
        YamlConfiguration merged=parse(originalText);
        int added=0;
        for(String key:bundled.getKeys(true)) {
            if(bundled.isConfigurationSection(key) || merged.contains(key,true))continue;
            // A scalar/list parent belongs to the user; set(path,...) would replace it.
            for(int dot=key.indexOf('.');dot>=0;dot=key.indexOf('.',dot+1)) {
                String parent=key.substring(0,dot);
                if(merged.contains(parent,true)&&!merged.isConfigurationSection(parent))
                    throw new IOException("Configuration structure conflicts with additive defaults");
            }
            Object value=bundled.get(key);
            if(key.startsWith("checks.") && key.indexOf('.',7)<0) {
                try {
                    Check check=Check.valueOf(key.substring(7).toUpperCase(Locale.ROOT));
                    value=effective.modes().get(check).name();
                } catch(IllegalArgumentException unknownCheck) {
                    throw new IOException("Bundled configuration contains an unsupported check",unknownCheck);
                }
            }
            merged.set(key,value);
            merged.setComments(key,bundled.getComments(key));
            merged.setInlineComments(key,bundled.getInlineComments(key));
            added++;
        }
        Policy.read(merged);
        if(added==0)return new Result(0,null);
        String serialized=merged.saveToString();
        Policy.read(parse(serialized));
        // Bukkit may normalize unsupported YAML structures (for example null map
        // entries). Reject any lossy rewrite rather than silently removing user data.
        var yaml=new Yaml(new SafeConstructor(new LoaderOptions()));
        if(!preserved(yaml.load(originalText),yaml.load(serialized)))
            throw new IOException("Configuration cannot be extended without changing existing values");
        byte[] replacement=serialized.getBytes(StandardCharsets.UTF_8);
        requireUnchanged(target,original);
        Path backup=target.resolveSibling(target.getFileName()+".backup-"+UUID.randomUUID()+".yml");
        writeNew(backup,original);
        Path temporary=target.resolveSibling(target.getFileName()+".migration-"+UUID.randomUUID()+".tmp");
        boolean created=false;
        try {
            writeNew(temporary,replacement);created=true;
            requireUnchanged(target,original);
            try {
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException unsupported) {
                requireUnchanged(target,original);
                Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
            }
            return new Result(added,backup);
        } finally {
            if(created)Files.deleteIfExists(temporary);
        }
    }

    private static YamlConfiguration parse(String text) throws InvalidConfigurationException {
        var config=new YamlConfiguration();config.options().parseComments(true);config.loadFromString(text);return config;
    }
    private static String decode(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static void requireUnchanged(Path target,byte[] original) throws IOException {
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(original,Files.readAllBytes(target)))
            throw new IOException("Configuration changed during migration; replacement cancelled");
    }
    private static void writeNew(Path path,byte[] bytes) throws IOException {
        try(var channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())channel.write(data);channel.force(true);
        }
    }
    private static boolean preserved(Object original,Object merged) {
        if(original instanceof Map<?,?> before) {
            if(!(merged instanceof Map<?,?> after))return false;
            for(var entry:before.entrySet())
                if(!after.containsKey(entry.getKey())||!preserved(entry.getValue(),after.get(entry.getKey())))return false;
            return true;
        }
        return Objects.equals(original,merged);
    }
}
