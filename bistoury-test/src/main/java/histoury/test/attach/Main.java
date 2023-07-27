package histoury.test.attach;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("args="+ Arrays.toString(args));
        new Main().test();

        Thread.currentThread().join();
    }

    public void test() throws InterruptedException {
        Thread.sleep(5 * 1000);
        System.out.println("com.study.javaagent\t" + 111222);
    }
}
