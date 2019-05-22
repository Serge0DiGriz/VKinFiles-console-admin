import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class Downloading extends Thread {
    static int counter = 0;
    private Attachment item;
    private MessageHandler main;
    private String path;

    Downloading(MessageHandler main, Attachment item, String path) {
        counter++;
        this.main = main; this.item = item; this.path = path;

        if ((new File(path)).mkdirs())
            System.out.println("Downloading directory was created! (" + path + ")");
    }

    @Override
    public void run() {
        try {
            URL url = new URL(item.url);

            ReadableByteChannel readChannel = Channels.newChannel(url.openStream());
            FileOutputStream fileOut = new FileOutputStream(path + item.title);

            fileOut.getChannel().transferFrom(readChannel, 0, Long.MAX_VALUE);
            readChannel.close(); fileOut.close();

            System.out.println("Download complete: " + item.title + " (" + item.url + ")");
        } catch (FileNotFoundException error) {
            System.out.println("Permission error! " + item.url);
        } catch (IOException error) {
            System.out.println(error.getMessage() + " (" + item.url + ")");
        }

        counter--; main.finishDownloading();
    }
}
