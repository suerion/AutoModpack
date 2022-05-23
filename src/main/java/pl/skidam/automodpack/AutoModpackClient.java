package pl.skidam.automodpack;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public class AutoModpackClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("AutoModpack");
    public static String AutoModpackUpdated;
    public static String ModpackUpdated;
    public static boolean Checking;
    public static final String link = "http://130.61.177.253/download/modpack.zip";
    public static final File out = new File("./AutoModpack/modpack.zip");
    public static final String selfLink = "https://github.com/Skidamek/AutoModpack/releases/download/pipel/AutoModpack.jar";
    public static final File selfOut = new File( "./mods/AutoModpack.jar");

    @Override
    public void onInitializeClient() {

        LOGGER.info("Initializing AutoModpack...");

        // Internet connection check
        while (true) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://www.google.com").openConnection();
                connection.setRequestMethod("HEAD");
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    break;
                }
                throw new Exception("AutoModpack -- Internet isn't available, Failed to get code 200 from " + connection.getURL().toString());
            } catch (Exception e) {
                System.err.println("AutoModpack -- Make sure that you have an internet connection!");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // TODO clean up this trash code!!!!
        // TODO add chad integration to the server who when you join the server, it will download the mods and update the mods by ping the server

        new Thread(new StartAndCheck(true)).start();
    }
}