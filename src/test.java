
import ru.ifmo.ctddev.Zemtsov.crawler.*;
import java.io.IOException;

/**
 * Created by vlad on 19.03.17.
 */
public class test {

    public static void main(String[] args) throws IOException {
        WebCrawler webCrawler = new WebCrawler(new CachingDownloader(), 10, 10, 2);
        Result result = webCrawler.download("http://www.kgeorgiy.info/", 2);
        System.out.println(result.getDownloaded());
        webCrawler.close();
    }
}
