package org.dimdev.riftloader;

import net.minecraft.launchwrapper.Launch;
import org.dimdev.utils.ReflectionUtils;

import javax.swing.*;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Main {
    private static final String[] LIBRARIES = {
            "https://www.dimdev.org/maven/org/dimdev/mixin/0.7.11-SNAPSHOT/mixin-0.7.11-SNAPSHOT.jar",
            "https://repo1.maven.org/maven2/org/ow2/asm/asm/6.2/asm-6.2.jar",
            "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/6.2/asm-commons-6.2.jar",
            "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/6.2/asm-tree-6.2.jar",
            "https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar"
    };
    public static final String VANILLA_SERVER = "https://launcher.mojang.com/v1/objects/3737db93722a9e39eeada7c27e7aca28b144ffa7/server.jar";
    // public static final String SPIGOT_SERVER = "https://cdn.getbukkit.org/spigot/spigot-1.13.jar";

    public static void main(String... args) throws Throwable {
        if (args.length == 0) {
        	runClientInstaller(false);
        } else if (args[0].equals("--install")) {
        	runClientInstaller(true);
        } else if (args[0].equals("--server")) {
            File serverJar = new File("server.jar");
            if (!serverJar.isFile()) {
                System.out.println("File 'server.jar' does not exist");
                System.out.println("Choose which server you'd like to download:");
                System.out.println("  1) Vanilla");
                // System.out.println("  2) Spigot");
                System.out.print("Choice: ");

                URL url;
                String line = new Scanner(System.in).nextLine().toLowerCase();
                if (line.startsWith("1") || line.startsWith("v")) {
                    url = new URL(VANILLA_SERVER);
//                } else if (line.startsWith("2") || line.startsWith("s")) {
//                    url = new URL(SPIGOT_SERVER);
                } else {
                    System.err.println("Not a valid choice");
                    return;
                }

                System.out.println("Downloading server jar: " + url);
                new FileOutputStream(serverJar).getChannel().transferFrom(Channels.newChannel(url.openStream()), 0, Long.MAX_VALUE);
            }

            ReflectionUtils.addURLToClasspath(serverJar.toURI().toURL());

            for (String url : LIBRARIES) {
                ReflectionUtils.addURLToClasspath(getOrDownload(new File("libs"), new URL(url)).toURI().toURL());
            }

            List<String> argsList = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
            argsList.add("--tweakClass");
            argsList.add("org.dimdev.riftloader.launch.RiftLoaderServerTweaker");

            System.out.println("Launching server...");
            Launch.main(argsList.toArray(new String[0]));
        }
    }

    private static File getOrDownload(File directory, URL url) throws IOException {
        String urlString = url.toString();
        File target = new File(directory, urlString.substring(urlString.lastIndexOf('/') + 1));
        if (target.isFile()) {
            return target;
        }
        target.getParentFile().mkdirs();

        System.out.println("Downloading library: " + urlString);
        new FileOutputStream(target).getChannel().transferFrom(Channels.newChannel(url.openStream()), 0, Long.MAX_VALUE);

        return target;
    }

    public static void runClientInstaller(boolean ask) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            File minecraftFolder;
            String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) {
                minecraftFolder = new File(System.getenv("APPDATA") + "/.minecraft");
            } else if (osName.contains("mac")) {
                minecraftFolder = new File(System.getProperty("user.home") + "/Library/Application Support/minecraft");
            } else {
                minecraftFolder = new File(System.getProperty("user.home") + "/.minecraft");
            }

            if (ask) {
	            JFileChooser dlg = new JFileChooser(minecraftFolder);
	            dlg.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	            dlg.setDialogTitle("Select install directory");
	            int res = dlg.showOpenDialog(null);
	            if (res == JFileChooser.APPROVE_OPTION) {
	            	minecraftFolder = dlg.getSelectedFile();
	            } else {
	            	return; //Cancelled picking an install directory
	            }
            }

            // Copy the version json
            File versionJson = new File(minecraftFolder, "versions/1.13.2-rift-1.0.4-SNAPSHOT/1.13.2-rift-1.0.4-SNAPSHOT.json");
            versionJson.getParentFile().mkdirs();
            Files.copy(Main.class.getResourceAsStream("/profile.json"), versionJson.toPath(), StandardCopyOption.REPLACE_EXISTING);

            File fakeJar = new File(minecraftFolder, "versions/1.13.2-rift-1.0.4-SNAPSHOT/1.13.2-rift-1.0.4-SNAPSHOT.jar");
            File maybeRealJar = new File(minecraftFolder, "versions/1.13.2/1.13.2.jar");
            if (maybeRealJar.exists()) {
            	Files.copy(maybeRealJar.toPath(), fakeJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
            	fakeJar.createNewFile();
            }

            // Make mods directory
            try {
                File modsFolder = new File(minecraftFolder, "mods");
                modsFolder.mkdirs();
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // Add rift as a profile
            try {
                File profilesJson = new File(minecraftFolder, "launcher_profiles.json");
                if (profilesJson.exists()) { // TODO: use gson instead
                    String contents = new String(Files.readAllBytes(profilesJson.toPath()));
                    if (contents.contains("\"rift\"")) {
                        contents = contents.replaceAll(",\n *\"rift\": \\{[^}]*},", ",");
                        contents = contents.replaceAll(",?\n *\"rift\": \\{[^}]*},?", "");
                    }
                    if (contents.contains("\"Rift\"")) {
                        contents = contents.replaceAll(",\n *\"Rift\": \\{[^}]*},", ",");
                        contents = contents.replaceAll(",?\n *\"Rift\": \\{[^}]*},?", "");
                    }

                    contents = contents.replace("\n  \"profiles\": {", "\n  \"profiles\": {\n" +
                                                                       "    \"Rift\": {\n" +
                                                                       "      \"name\": \"Rift\",\n" +
                                                                       "      \"type\": \"custom\",\n" +
                                                                       "      \"created\": \"2018-08-13T00:00:00.000Z\",\n" +
                                                                       "      \"lastUsed\": \"2100-01-01T00:00:00.000Z\",\n" +
                                                                       "      \"lastVersionId\": \"1.13.2-rift-1.0.4-SNAPSHOT\"\n" +
                                                                       "    },");

                    Files.write(profilesJson.toPath(), contents.getBytes());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // Copy rift jar to libraries
            try {
                String source = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                if (source.startsWith("/") && osName.contains("win")) {
                    source = source.substring(1);
                }
                File riftJar = new File(minecraftFolder, "libraries/org/dimdev/rift/1.0.4-SNAPSHOT/rift-1.0.4-SNAPSHOT.jar");
                riftJar.getParentFile().mkdirs();
                Files.copy(Paths.get(source), riftJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                t.printStackTrace();
            }

            JOptionPane.showMessageDialog(null,
                    "Rift 1.0.4-SNAPSHOT for Minecraft 1.13.2 has been successfully installed" +
                    (ask ? " to\n" + minecraftFolder.getAbsolutePath() + "\n" : "!\n") +
                    "\n" +
                    "It is available in the dropdown menu of the vanilla Minecraft launcher.\n" +
                    "You'll need to restart the Minecraft Launcher if you had it open when\n" +
                    "you ran this installer.",
                    "Rift Installer", JOptionPane.INFORMATION_MESSAGE);
        } catch (Throwable t) {
            StringWriter w = new StringWriter();
            t.printStackTrace(new PrintWriter(w));
            JOptionPane.showMessageDialog(null,
                    "An error occured while installing Rift, please report this to the issue\n" +
                    "tracker (https://github.com/DimensionalDevelopment/Rift/issues):\n" +
                    "\n" +
                    w.toString().replace("\t", "    "), "Rift Installer", JOptionPane.ERROR_MESSAGE);
        }
    }
}
