package jdbchelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.PrintWriter;

/**
 * User: erdinc
 * Date: 23-Nov-2009
 * Time: 16:05:21
 */
public class PooledDataSource implements DataSource {

   ConnectionPool pool;

   public PooledDataSource(ConnectionPool pool) {
      this.pool = pool;
   }

   public Connection getConnection() throws SQLException {
      return pool.getConnection();
   }

   public Connection getConnection(String username, String password) throws SQLException {
      return pool.getConnection();
   }

   PrintWriter logWriter;

   public PrintWriter getLogWriter() throws SQLException {
      if (logWriter == null) {
         logWriter = new PrintWriter(System.out);
      }

      return logWriter;
   }

   public void setLogWriter(PrintWriter out) throws SQLException {
      logWriter = out;
   }

   public void setLoginTimeout(int seconds) throws SQLException {
   }

   public int getLoginTimeout() throws SQLException {
      return 0;
   }

   public <T> T unwrap(Class<T> iface) throws SQLException {
      return null;
   }

   public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return false;
   }
}
