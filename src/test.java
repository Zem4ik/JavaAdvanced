
import javafx.util.Pair;
import ru.ifmo.ctddev.Zemtsov.crawler.*;
import java.io.IOException;
import java.util.Map;
import info.kgeorgiy.java.advanced.crawler.*;

/**
 * Created by vlad on 19.03.17.
 */
public class test {

    public static void main(String[] args) throws IOException {
        WebCrawler webCrawler = new WebCrawler(new CachingDownloader(), 2147483647, 2147483647, 2147483647);
        Result result = webCrawler.download("http://www.kgeorgiy.info", 4);
        System.out.println(result.getDownloaded().size());
        result.getDownloaded().forEach(System.out::println);
        System.out.println(result.getErrors().size());
        System.out.println(result.getDownloaded().size());
        //for (Map.Entry pair : result.getErrors().entrySet()) {
        //    System.out.println(pair.getKey() + " ## " + pair.getValue());
        //}
        webCrawler.close();
    }
}
