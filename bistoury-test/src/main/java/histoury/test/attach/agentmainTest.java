package histoury.test.attach;

import java.lang.instrument.Instrumentation;

public class agentmainTest {
    public static void premain(String args, Instrumentation inst) {
        System.out.println("premain");
    }

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("agentmain");
    }

//    public static void main(String[] args) {
//        System.out.println("args="+args);
//    }
}
