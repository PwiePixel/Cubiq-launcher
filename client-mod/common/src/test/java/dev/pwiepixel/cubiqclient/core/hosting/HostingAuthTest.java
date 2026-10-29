package dev.pwiepixel.cubiqclient.core.hosting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gehostete Welten verlangen immer die Mojang-Anmeldung der Gäste (online-mode): Es gibt keinen Schalter, der sie
 * abschaltet – weder in der Schnittstelle noch in einem Loader-Baum, auch nicht für Tests per System-Property.
 * Veröffentlichte Jars können die Anmeldung damit nicht abschalten.
 */
class HostingAuthTest {
	/** Abschalten der Server-Anmeldung (alle Mappings) bzw. der frühere Test-Schalter. */
	private static final Pattern AUTH_OFF = Pattern.compile(
			"setUsesAuthentication\\s*\\(\\s*false|setOnlineMode\\s*\\(\\s*false|cubiqclient\\.hosting\\.offline");

	@Test
	void platformHasNoSwitchForTheServerLogin() {
		for (Method m : HostingPlatform.class.getMethods()) {
			String name = m.getName().toLowerCase(Locale.ROOT);
			assertTrue(!name.contains("offline") && !name.contains("authentication") && !name.contains("onlinemode"),
					"HostingPlatform." + m.getName());
		}
	}

	@Test
	void noLoaderTreeSwitchesTheServerLoginOff() throws IOException {
		Path root = clientModRoot();
		List<String> found = new ArrayList<String>();
		int files = 0;
		for (String tree : Arrays.asList("common", "fabric", "neoforge", "forge", "forge-mojmap-legacy", "legacy",
				"legacy-1.7.10", "forge-1.13.2")) {
			Path src = root.resolve(tree).resolve("src").resolve("main").resolve("java");
			if (!Files.isDirectory(src)) continue;
			List<Path> java = new ArrayList<Path>();
			try (Stream<Path> walk = Files.walk(src)) {
				walk.filter(p -> p.toString().endsWith(".java")).forEach(java::add);
			}
			for (Path p : java) {
				files++;
				if (AUTH_OFF.matcher(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).find()) {
					found.add(root.relativize(p).toString());
				}
			}
		}
		assertTrue(files > 100, "Quellbäume nicht gefunden (" + files + " Dateien)");
		assertTrue(found.isEmpty(), "schaltet die Anmeldung der Gäste ab: " + found);
	}

	/** client-mod/ – Tests laufen in common/ (oder in forge/, neoforge/ mit eingebundenen Tests). */
	private static Path clientModRoot() {
		Path p = Paths.get("").toAbsolutePath();
		for (int i = 0; i < 4 && p != null; i++, p = p.getParent()) {
			if (Files.isDirectory(p.resolve("common").resolve("src"))) return p;
		}
		return Paths.get("..").toAbsolutePath().normalize();
	}
}
