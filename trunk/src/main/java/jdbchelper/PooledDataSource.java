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

   @Override
   public Connection getConnection() throws SQLException {
      return pool.getConnection();
   }

   @Override
   public Connection getConnection(String username, String password) throws SQLException {
      return pool.getConnection();
   }

   PrintWriter logWriter;

   @Override
   public PrintWriter getLogWriter() throws SQLException {
      if (logWriter == null) {
         logWriter = new PrintWriter(System.out);
      }

      return logWriter;
   }

   @Override
   public void setLogWriter(PrintWriter out) throws SQLException {
      logWriter = out;
   }

   @Override
   public void setLoginTimeout(int seconds) throws SQLException {
   }

   @Override
   public int getLoginTimeout() throws SQLException {
      return 0;
   }

   @Override
   public <T> T unwrap(Class<T> iface) throws SQLException {
      return null;
   }

   @Override
   public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return false;
   }
}
