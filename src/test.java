import ru.ifmo.ctddev.Zemtsov.concurrent.IterativeParallelism;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by vlad on 19.03.17.
 */
public class test {

    public static void main(String[] args) {
        int[] b = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 122, 123, 124, 125};
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int c : b) {
            list.add(c);
        }

        int a = -1;
        try {
            a = new IterativeParallelism().<Integer>maximum(4, list, null);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(a);
    }
    //:<2055322126> but was:<655101749>
}
