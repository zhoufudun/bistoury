package qunar.tc.bistoury.proxy.container;

import org.junit.Test;

/**
 * @author xkrivzooh
 * @since 2019/8/15
 */
public class ProxyBootstrapForTest {

	@Test
	public void bootstrap() {
		String bistouryConfPath = ProxyBootstrapForTest.class.getResource("/conf").getPath().toString();
		System.setProperty("bistoury.conf", bistouryConfPath);
		try {
			ProxyBootstrapMain.main(new String[]{});
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}