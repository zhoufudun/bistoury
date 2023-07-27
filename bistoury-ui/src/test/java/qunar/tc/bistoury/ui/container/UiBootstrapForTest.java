package qunar.tc.bistoury.ui.container;

import org.junit.Test;

/**
 * @author xkrivzooh
 * @since 2019/8/15
 */
public class UiBootstrapForTest {

	@Test
	public void bootstrap() {
		String bistouryConfPath = UiBootstrapForTest.class.getResource("/conf").getPath().toString();
		System.setProperty("bistoury.conf", bistouryConfPath);
		try {
			UIBootstrapMain.main(new String[]{});
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}


}