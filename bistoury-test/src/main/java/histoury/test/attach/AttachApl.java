package histoury.test.attach;


import com.sun.tools.attach.*;

import java.io.IOException;
import java.util.Properties;

public class AttachApl {
    public static void main(String[] args) throws IOException {

        int attachPid=23468;
        String agentPath="D:\\code\\bistoury\\bistoury-test\\target\\agent-test.jar";
//        String agentPath="";

        VirtualMachineDescriptor virtualMachineDescriptor = null;
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            String pid = descriptor.id();
            if (pid.equals(String.valueOf(attachPid))) {
                virtualMachineDescriptor = descriptor;
                System.out.println(descriptor);
            }
        }
        VirtualMachine virtualMachine = null;
        try {
            if (virtualMachineDescriptor == null) {
                virtualMachine = VirtualMachine.attach(String.valueOf(attachPid));
            } else {
                virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
            }

            Properties targetSystemProperties = virtualMachine.getSystemProperties();
            String targetJavaVersion = targetSystemProperties.getProperty("java.specification.version");
            String currentJavaVersion = System.getProperty("java.specification.version");
            if (targetJavaVersion != null && currentJavaVersion != null) {
                if (!targetJavaVersion.equals(currentJavaVersion)) {
                }
            }
            System.out.println(targetJavaVersion);
            System.out.println(currentJavaVersion);

            System.out.println(virtualMachine.getSystemProperties().toString());

            virtualMachine.loadAgent(agentPath);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (AttachNotSupportedException e) {
            e.printStackTrace();
        } catch (AgentInitializationException e) {
            e.printStackTrace();
        } catch (AgentLoadException e) {
            e.printStackTrace();
        } finally {
            if (virtualMachine != null) {
                virtualMachine.detach();
            }
        }
    }
}
