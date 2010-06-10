package jdbchelper;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User: erdinc
 * Date: Oct 9, 2009
 * Time: 3:13:16 PM
 */
public class LoadBalancingDataSource implements DataSource {

   static class Pool {
      final String name;
      final ConnectionPool pool;

      Pool(String name, ConnectionPool pool) {
         this.name = name;
         this.pool = pool;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Pool pool = (Pool) o;

         return name.equals(pool.name);
      }

      @Override
      public int hashCode() {
         return name.hashCode();
      }

      public String toString() {
         return name;
      }
   }

   ArrayList<Pool> connectionPools;
   ArrayList<Pool> invalidConnectionPools;

   public LoadBalancingDataSource() {
      connectionPools = new ArrayList<Pool>();
      invalidConnectionPools = new ArrayList<Pool>();
   }

   public void addDataSource(String name, ConnectionPoolDataSource dataSource, int maxConnections, int timeout) {
      ConnectionPool pool = new ConnectionPool(dataSource, maxConnections, timeout);
      connectionPools.add(new Pool(name, pool));
   }

   public void addDataSource(String name, ConnectionPoolDataSource dataSource, int maxConnections) {
      addDataSource(name, dataSource, maxConnections, 60);
   }

   public synchronized void removeDataSource(String name) {
      connectionPools.remove(new Pool(name, null));
   }

   Random random = new Random();

   public Connection getConnection() throws SQLException {
      Exception latestCause = null;
      for(int i = 0; i < 3 && connectionPools.size() != 0; i++) {
         Pool p = null;
         try {
            p = connectionPools.get(Math.abs(random.nextInt() % connectionPools.size()));
            return p.pool.getConnection();
         } catch (IndexOutOfBoundsException e) {
            latestCause = e;
         } catch (SQLException e) {
            if(p != null) {
               p.pool.dispose();
               connectionPools.remove(p);
               invalidConnectionPools.add(p);
            }

            latestCause = e;
         }
      }

      if(latestCause != null) {
         throw new SQLException("LoadBalancingDataSource: Could not get a connection, Error: " + latestCause.getMessage());
      } else {
         throw new SQLException("LoadBalancingDataSource: Could not get a connection");
      }
   }

   public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
   }

   public Runnable getMaintenanceJob() {
      return new Runnable() {
         public void run() {
            checkInvalidPools();
            for(Pool p : connectionPools) {
               p.pool.freeIdleConnections();
            }
         }
      };
   }

   private void checkInvalidPools() {
      Iterator<Pool> iterator = invalidConnectionPools.iterator();
      while(iterator.hasNext()) {
         Pool p = iterator.next();
         try {
            Connection con = p.pool.getConnection();
            con.close();
            connectionPools.add(p);
            iterator.remove();
         } catch (SQLException e) {
            //
         }
      }
   }

   ScheduledExecutorService executorService;

   public void startEvictionThread() {
      if(executorService == null || executorService.isShutdown()) {
         executorService = Executors.newSingleThreadScheduledExecutor();
      }

      executorService.scheduleAtFixedRate(getMaintenanceJob(), 0, 5, TimeUnit.MINUTES);
   }

   public void stopEvictionThread() {
      if(executorService == null || executorService.isShutdown()) {
         return;
      }

      executorService.shutdownNow();
      try {
         executorService.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         //
      }
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
      //
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
