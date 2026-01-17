package wsmc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class WsmcConfig {
    private static final String CONFIG_DIR = "config/wsmc";
    private static final String CONFIG_FILE = "wsmc.properties";

    // Properties
    public static int sslPort = 443;
    public static String certDir = "config/wsmc";

    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;

        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File file = new File(configDir, CONFIG_FILE);
        Properties props = new Properties();

        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                WSMC.info("Failed to load config file: " + e.getMessage());
            }
        } else {
            // Write default config
            saveDefaultConfig(file);
        }

        parseConfig(props);
        loaded = true;
    }

    private static void parseConfig(Properties props) {
        try {
            String portStr = props.getProperty("sslPort", "443");
            sslPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            WSMC.info("Invalid sslPort in config, defaulting to 443");
            sslPort = 443;
        }

        certDir = props.getProperty("certDir", CONFIG_DIR);
        // Ensure certDir is absolute or relative to run directory correctly?
        // We will treat it as relative to working directory if not absolute.
    }

    private static void saveDefaultConfig(File file) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("# WSMC SSL Configuration\n");
            content.append("sslPort=443\n");
            content.append("certDir=config/wsmc\n");

            Files.writeString(file.toPath(), content.toString());
        } catch (IOException e) {
            WSMC.info("Failed to save default config: " + e.getMessage());
        }
    }

    public static Path getCertPath() {
        return Paths.get(certDir);
    }
}
