package qunar.tc.bistoury.commands.arthas.telnet;

import org.junit.Test;
import qunar.tc.bistoury.commands.arthas.ArthasStarter;

public class TestTelnet {
    @Test
    public void attachAgent() {
        try {
            ArthasStarter.start(21404);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
