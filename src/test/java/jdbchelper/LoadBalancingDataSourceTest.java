package jdbchelper;

import org.junit.Test;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import static junit.framework.Assert.assertEquals;

/**
 * User: erdinc
 * Date: Oct 9, 2009
 * Time: 5:56:01 PM
 */
public class LoadBalancingDataSourceTest {
   @Test
   public void test() {
      MysqlConnectionPoolDataSource ds = new MysqlConnectionPoolDataSource();
      ds.setServerName("localhost");
      ds.setUser("root");
      ds.setPassword("");
      ds.setDatabaseName("test");

      LoadBalancingDataSource lbDs = new LoadBalancingDataSource();
      lbDs.addDataSource("first", ds, 10);

      ds = new MysqlConnectionPoolDataSource();
      ds.setServerName("localhost");
      ds.setUser("root");
      ds.setPassword("");
      ds.setDatabaseName("test");
      lbDs.addDataSource("second", ds, 10);

      JdbcHelper jdbc = new JdbcHelper(lbDs);
      for(int i = 0; i < 10; i++) {
         assertEquals(1, jdbc.queryForInt("select 1"));
      }

      lbDs.removeDataSource("first");

      for(int i = 0; i < 10; i++) {
         assertEquals(1, jdbc.queryForInt("select 1"));
      }
   }
}
