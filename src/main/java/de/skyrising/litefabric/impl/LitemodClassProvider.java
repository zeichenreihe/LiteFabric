package de.skyrising.litefabric.impl;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

class LitemodClassProvider {
    private static final Logger LOGGER = LogManager.getLogger("LiteFabric|ClassProvider");
    private static final String SCREEN_CLASS = classNameOf("class_5641");
    private static final Set<String> CONFIG_GUI_SUPER_CLASSES = new HashSet<>(Arrays.asList(
        "fi/dy/masa/malilib/gui/GuiConfigsBase",
        SCREEN_CLASS
    ));
    private static final boolean DUMP = true;

    private final LitemodContainer mod;
    private final FileSystem fileSystem;
    private final LitemodRemapper remapper;
    private final Map<String, byte[]> classByteCache = new HashMap<>();

    public LitemodClassProvider(LitemodContainer mod, FileSystem fileSystem, LitemodRemapper remapper) {
        this.mod = mod;
        this.fileSystem = fileSystem;
        this.remapper = remapper;
    }

    byte[] getClassBytes(String name) {
        byte[] cached = classByteCache.get(name);
        if (cached != null) return cached;
        String classFileName = name.replace('.', '/') + ".class";
        Path classFilePath = fileSystem.getPath(classFileName);
        if (!Files.exists(classFilePath)) return null;
        byte[] rawBytes;
        try {
            rawBytes = Files.readAllBytes(classFilePath);
        } catch (IOException e) {
            return null;
        }
        ClassReader reader = new ClassReader(rawBytes);
        ClassWriter writer = new ClassWriter(reader, Opcodes.ASM9);
        ClassNode raw = new ClassNode();
        reader.accept(raw, ClassReader.EXPAND_FRAMES);
        remapper.addClass(raw);
        ClassNode remapped = new ClassNode();
        ClassRemapper clsRemapper = new ClassRemapper(remapped, remapper);
        raw.accept(clsRemapper);
        if (isConfigGuiCandidate(remapped)) {
            mod.configGuiCandidates.add(remapped.name);
        }
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            if (Annotations.getInvisible(remapped, Mixin.class) != null) {
                for (MethodNode method : remapped.methods) {
                    if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
                    if (method.visibleAnnotations == null || Annotations.getVisible(method, Overwrite.class) != null) {
                        Bytecode.setVisibility(method, Opcodes.ACC_PUBLIC);
                    }
                }
            }
        }
        remapped.accept(writer);
        byte[] bytes = writer.toByteArray();
        classByteCache.put(name, bytes);
        if (DUMP) {
            Path out = Paths.get(".litefabric.out/class/" + classFileName);
            try {
                Files.createDirectories(out.getParent());
                Files.write(out, bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bytes;
    }

    private static boolean isConfigGuiCandidate(ClassNode node) {
        if (Annotations.getInvisible(node, Mixin.class) != null) return false;
        if ((node.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)) != Opcodes.ACC_PUBLIC) return false;
        return CONFIG_GUI_SUPER_CLASSES.contains(node.superName);
    }

    ClassNode getClassNode(String name) {
        byte[] bytes = getClassBytes(name);
        if (bytes == null) return null;
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    URL findResource(String name) {
        try {
            if (name.endsWith(".class")) {
                Path tmpPath = LiteFabric.TMP_FILES.getPath(name);
                if (Files.exists(tmpPath)) return tmpPath.toUri().toURL();
                byte[] classBytes = getClassBytes(name.substring(0, name.length() - 6));
                if (classBytes != null) {
                    Files.createDirectories(tmpPath.getParent());
                    Files.write(tmpPath, classBytes);
                    return tmpPath.toUri().toURL();
                }
                return null;
            }
            Path path = fileSystem.getPath(name);
            if (Files.exists(path)) {
                if (DUMP) {
                    Path out = Paths.get(".litefabric.out/resource/" + name);
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
                }
                URI uri = path.toUri();
                String uriStr = uri.toString();
                if (uriStr.contains("%25")) {
                    uriStr = uriStr.replaceAll("%25", "%");
                    try {
                        uri = new URI(uriStr);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
                return uri.toURL();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    private static String classNameOf(String intermediary) {
        return FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", "net.minecraft." + intermediary).replace('.', '/');
    }
}
