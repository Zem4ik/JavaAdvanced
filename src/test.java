
import javafx.util.Pair;
import ru.ifmo.ctddev.Zemtsov.crawler.*;
import java.io.IOException;
import java.util.Map;
import info.kgeorgiy.java.advanced.crawler.*;
import ru.ifmo.ctddev.Zemtsov.hello.HelloUDPClient;
import ru.ifmo.ctddev.Zemtsov.hello.HelloUDPServer;

/**
 * Created by vlad on 19.03.17.
 */
public class test {

    public static void main(String[] args) throws IOException {
        HelloUDPClient client = new HelloUDPClient();
        HelloUDPServer server = new HelloUDPServer();
        server.start(60000, 2);
        client.start("localhost", 60000, "Vlad", 5, 5);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        server.close();
    }
}
