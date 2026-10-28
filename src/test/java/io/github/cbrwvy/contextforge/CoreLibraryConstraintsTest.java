package io.github.cbrwvy.contextforge;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CoreLibraryConstraintsTest {

    private static final List<String> FORBIDDEN_API_NAMES = List.of(
            "java/net/Socket",
            "java/net/URL",
            "java/net/http/HttpClient");

    @Test
    void compiledCoreDoesNotReferenceNetworkClients() throws IOException, URISyntaxException {
        var source = Identifier.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            fail("Core classes have no code source");
        }
        Path root = Path.of(source.getLocation().toURI());
        List<String> hits = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> files = Files.walk(root)) {
                List<Path> classFiles = files
                        .filter(path -> path.getFileName().toString().endsWith(".class"))
                        .toList();
                for (Path classFile : classFiles) {
                    collectHits(classFile.toString(), Files.readAllBytes(classFile), hits);
                }
            }
        } else if (root.getFileName().toString().endsWith(".jar")) {
            try (JarFile jar = new JarFile(root.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class") || entry.isDirectory()) {
                        continue;
                    }
                    try (InputStream input = jar.getInputStream(entry)) {
                        collectHits(entry.getName(), input.readAllBytes(), hits);
                    }
                }
            }
        } else {
            fail("Unrecognized core output: " + root);
        }

        assertTrue(hits.isEmpty(), () -> "Network APIs referenced by core classes: " + hits);
    }

    private static void collectHits(String name, byte[] classBytes, List<String> hits) {
        String content = new String(classBytes, StandardCharsets.ISO_8859_1);
        for (String token : FORBIDDEN_API_NAMES) {
            if (content.contains(token)) {
                hits.add(name + " -> " + token);
            }
        }
    }
}
