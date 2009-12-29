package jdbchelper;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Author: Erdinc YILMAZEL
 * Date: Dec 25, 2008
 * Time: 12:36:52 AM
 */
public class JdbcHelper {
   DataSource dataSource;

   static Logger logger = Logger.getLogger("jdbchelper");

   public JdbcHelper(DataSource dataSource) {
      this.dataSource = dataSource;
   }

   public void freeConnection(Connection con) {
      if (!isConnectionHeld()) {
         JdbcUtil.close(con);
      }
   }

   public Connection getConnection() throws SQLException {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);

      if (transaction == null) {
         return dataSource.getConnection();
      } else {
         return transaction.connection;
      }
   }

   abstract class QueryCallback<T> {
      public abstract T process(ResultSet rs) throws SQLException;

      public T noResult() {
         return null;
      }

      public int getFetchSize() {
         return 0;
      }

      public int getMaxRows() {
         return 0;
      }

      public int getTimeout() {
         return 0;
      }
   }

   abstract class ParameteredQueryCallback<T> extends QueryCallback<T> {
      ResultSetHandler handler;

      protected ParameteredQueryCallback(ResultSetHandler handler) {
         this.handler = handler;
      }

      @Override
      public int getFetchSize() {
         return handler.fetchSize;
      }

      @Override
      public int getMaxRows() {
         return handler.maxRows;
      }

      @Override
      public int getTimeout() {
         return handler.timeOut;
      }
   }

   private Map<Thread, Transaction> transactions = new java.util.concurrent.ConcurrentHashMap<Thread, Transaction>();

   private static class Transaction {
      Connection connection;
      boolean autoCommit;
      int hold;

      Transaction(Connection connection, boolean autoCommit) {
         this.connection = connection;
         this.autoCommit = autoCommit;
      }
   }

   public boolean isConnectionHeld() {
      return transactions.containsKey(Thread.currentThread());
   }

   public boolean isInTransaction() {
      Transaction transaction = transactions.get(Thread.currentThread());
      return transaction != null && !transaction.autoCommit;
   }

   public void beginTransaction() {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);

      try {
         if (transaction == null) {
            transaction = new Transaction(dataSource.getConnection(), false);
         } else {
            transaction.autoCommit = false;
         }
         transaction.connection.setAutoCommit(false);

         transaction.hold++;

         transactions.put(current, transaction);
      } catch (SQLException e) {
         logger.warning("Error beginning transaction: " + e.getMessage());
         e.printStackTrace();
      }
   }

   public void holdConnection() {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);

      try {
         if (transaction == null) {
            transaction = new Transaction(dataSource.getConnection(), true);
         }

         transaction.hold++;

         transactions.put(current, transaction);
      } catch (SQLException e) {
         logger.warning("Error holding connection: " + e.getMessage());
         e.printStackTrace();
      }
   }

   public void releaseConnection() {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);
      if (transaction == null) {
         throw new RuntimeException("There isn't a current connection to release");
      }

      transaction.hold--;

      if (transaction.hold == 0) {

         if (!transaction.autoCommit) {
            try {
               transaction.connection.commit();
            } catch (SQLException e) {
               logger.warning("Error commiting transaction: " + e.getMessage());
               e.printStackTrace();
            }
         }

         JdbcUtil.close(transaction.connection);

         transactions.remove(current);
      }
   }

   public void commitTransaction() {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);

      if (transaction == null || transaction.autoCommit) {
         throw new RuntimeException("There isn't a current transaction to comit");
      } else {
         try {
            transaction.connection.commit();
         } catch (SQLException e) {
            logger.warning("Error commiting transaction: " + e.getMessage());
            e.printStackTrace();
         }

         JdbcUtil.close(transaction.connection);

         transactions.remove(current);
      }
   }

   public void rollbackTransaction() {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);

      if (transaction == null || transaction.autoCommit) {
         throw new RuntimeException("There isn't a current transaction to rollback");
      } else {
         try {
            transaction.connection.rollback();
         } catch (SQLException e) {
            logger.warning("Error rolling back transaction: " + e.getMessage());
            e.printStackTrace();
         }

         JdbcUtil.close(transaction.connection);

         transactions.remove(current);
      }
   }

   public long getLastInsertId() {
      Thread current = Thread.currentThread();
      Transaction transaction = transactions.get(current);

      if (transaction == null) {
         throw new RuntimeException("There isn't a current transaction");
      } else {
         try {
            return queryForLong("SELECT last_insert_id();");
         } catch (NoResultException e) {
            return 0;
         }
      }
   }

   protected <T> T genericQuery(String sql, QueryCallback<T> callback, Object... params) throws NoResultException {
      Connection con = null;
      Statement stmt = null;
      ResultSet result = null;

      try {
         con = getConnection();

         if (params.length == 0) {
            stmt = con.createStatement();

            if (callback.getFetchSize() != 0) {
               stmt.setFetchSize(callback.getFetchSize());
            }

            if (callback.getMaxRows() != 0) {
               stmt.setMaxRows(callback.getMaxRows());
            }

            if (callback.getTimeout() != 0) {
               stmt.setQueryTimeout(callback.getTimeout());
            }

            result = stmt.executeQuery(sql);
         } else {
            stmt = fillStatement(con.prepareStatement(sql), params);

            if (callback.getFetchSize() != 0) {
               stmt.setFetchSize(callback.getFetchSize());
            }

            if (callback.getMaxRows() != 0) {
               stmt.setMaxRows(callback.getMaxRows());
            }

            if (callback.getTimeout() != 0) {
               stmt.setQueryTimeout(callback.getTimeout());
            }
            result = ((PreparedStatement) stmt).executeQuery();
         }

         boolean n = result.next();
         if (!n) {
            throw new NoResultException();
         }

         do {
            T t = callback.process(result);
            if (t != null) {
               return t;
            }
         } while (result.next());


      } catch (SQLException e) {
         logger.warning("Error running query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error running query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt, result);
         freeConnection(con);
      }
      return null;
   }

   public <T> ArrayList<T> queryForList(String sql, final BeanCreator<T> beanCreator, Object... params) {
      final ArrayList<T> list = new ArrayList<T>();
      try {
         genericQuery(sql, new QueryCallback<T>() {
            public T process(ResultSet rs) throws SQLException {
               T t = beanCreator.createBean(rs);
               if(t instanceof JdbcAware) {
                  ((JdbcAware) t).setJdbcHelper(JdbcHelper.this);
               }
               list.add(t);
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }

   public ArrayList<Integer> queryForIntegerList(String sql, Object... params) {
      final ArrayList<Integer> list = new ArrayList<Integer>();
      try {
         genericQuery(sql, new QueryCallback<Integer>() {
            public Integer process(ResultSet rs) throws SQLException {
               Integer t = rs.getInt(1);
               list.add(t);
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }

   public ArrayList<String> queryForStringList(String sql, Object... params) {
      final ArrayList<String> list = new ArrayList<String>();
      try {
         genericQuery(sql, new QueryCallback<String>() {
            public String process(ResultSet rs) throws SQLException {
               String t = rs.getString(1);
               list.add(t);
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }

   public <X, Y> ArrayList<Tuple<X, Y>> queryForList(String sql,
                                                     final BeanCreator<X> xCreator,
                                                     final BeanCreator<Y> yCreator,
                                                     Object... params) {
      final ArrayList<Tuple<X, Y>> list = new ArrayList<Tuple<X, Y>>();
      try {
         genericQuery(sql, new QueryCallback<Tuple<X, Y>>() {
            public Tuple<X, Y> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               list.add(new Tuple<X, Y>(x, y));
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }

   public <X, Y, Z> ArrayList<Triple<X, Y, Z>> queryForList(String sql,
                                                     final BeanCreator<X> xCreator,
                                                     final BeanCreator<Y> yCreator,
                                                     final BeanCreator<Z> zCreator,
                                                     Object... params) {
      final ArrayList<Triple<X, Y, Z>> list = new ArrayList<Triple<X, Y, Z>>();
      try {
         genericQuery(sql, new QueryCallback<Triple<X, Y, Z>>() {
            public Triple<X, Y, Z> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               Z z = zCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               if(z instanceof JdbcAware) {
                  ((JdbcAware) z).setJdbcHelper(JdbcHelper.this);
               }
               list.add(new Triple<X, Y, Z>(x, y, z));
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }
   
   public <X, Y, Z, W> ArrayList<Quadruple<X, Y, Z, W>> queryForList(String sql,
                                                     final BeanCreator<X> xCreator,
                                                     final BeanCreator<Y> yCreator,
                                                     final BeanCreator<Z> zCreator,
                                                     final BeanCreator<W> wCreator,
                                                     Object... params) {
      final ArrayList<Quadruple<X, Y, Z, W>> list = new ArrayList<Quadruple<X, Y, Z, W>>();
      try {
         genericQuery(sql, new QueryCallback<Quadruple<X, Y, Z, W>>() {
            public Quadruple<X, Y, Z, W> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               Z z = zCreator.createBean(rs);
               W w = wCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               if(z instanceof JdbcAware) {
                  ((JdbcAware) z).setJdbcHelper(JdbcHelper.this);
               }
               if(w instanceof JdbcAware) {
                  ((JdbcAware) w).setJdbcHelper(JdbcHelper.this);
               }
               list.add(new Quadruple<X, Y, Z, W>(x, y, z, w));
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }

   public <X, Y, Z, W, Q> ArrayList<Pentuple<X, Y, Z, W, Q>> queryForList(String sql,
                                                     final BeanCreator<X> xCreator,
                                                     final BeanCreator<Y> yCreator,
                                                     final BeanCreator<Z> zCreator,
                                                     final BeanCreator<W> wCreator,
                                                     final BeanCreator<Q> qCreator,
                                                     Object... params) {
      final ArrayList<Pentuple<X, Y, Z, W, Q>> list = new ArrayList<Pentuple<X, Y, Z, W, Q>>();
      try {
         genericQuery(sql, new QueryCallback<Pentuple<X, Y, Z, W, Q>>() {
            public Pentuple<X, Y, Z, W, Q> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               Z z = zCreator.createBean(rs);
               W w = wCreator.createBean(rs);
               Q q = qCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               if(z instanceof JdbcAware) {
                  ((JdbcAware) z).setJdbcHelper(JdbcHelper.this);
               }
               if(w instanceof JdbcAware) {
                  ((JdbcAware) w).setJdbcHelper(JdbcHelper.this);
               }
               if(q instanceof JdbcAware) {
                  ((JdbcAware) q).setJdbcHelper(JdbcHelper.this);
               }
               list.add(new Pentuple<X, Y, Z, W, Q>(x, y, z, w, q));
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }
      return list;
   }

   public <K, V> HashMap<K, V> queryForMap(String sql, final ResultSetMapper<K, V> resultSetMapper, Object... params) {
      final HashMap<K, V> map = new HashMap<K, V>();
      try {
         genericQuery(sql, new QueryCallback<AbstractMap.SimpleEntry<K, V>>() {
            @Override
            public AbstractMap.SimpleEntry<K, V> process(ResultSet rs) throws SQLException {
               AbstractMap.SimpleEntry<K, V> entry = resultSetMapper.mapRow(rs);
               map.put(entry.getKey(), entry.getValue());
               return null;
            }
         }, params);
      } catch (NoResultException e) {
         //
      }

      return map;
   }

   public <T> T queryForObject(String sql, final BeanCreator<T> beanCreator, Object... params) {
      try {
         return genericQuery(sql, new QueryCallback<T>() {
            public T process(ResultSet rs) throws SQLException {
               T t = beanCreator.createBean(rs);
               if(t instanceof JdbcAware) {
                  ((JdbcAware) t).setJdbcHelper(JdbcHelper.this);
               }
               return t;
            }
         }, params);
      } catch (NoResultException e) {
         return null;
      }
   }

   public <X, Y> Tuple<X, Y> queryForTuple(String sql,
                                           final BeanCreator<X> xCreator,
                                           final BeanCreator<Y> yCreator,
                                           Object... params) {
      try {
         return genericQuery(sql, new QueryCallback<Tuple<X, Y>>() {
            public Tuple<X, Y> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               return new Tuple<X, Y>(x, y);
            }
         }, params);
      } catch (NoResultException e) {
         return null;
      }
   }

   public <X, Y, Z> Triple<X, Y, Z> queryForTriple(String sql,
                                           final BeanCreator<X> xCreator,
                                           final BeanCreator<Y> yCreator,
                                           final BeanCreator<Z> zCreator,
                                           Object... params) {
      try {
         return genericQuery(sql, new QueryCallback<Triple<X, Y, Z>>() {
            public Triple<X, Y, Z> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               Z z = zCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               if(z instanceof JdbcAware) {
                  ((JdbcAware) z).setJdbcHelper(JdbcHelper.this);
               }

               return new Triple<X, Y, Z>(x, y, z);
            }
         }, params);
      } catch (NoResultException e) {
         return null;
      }
   }

   public <X, Y, Z, W> Quadruple<X, Y, Z, W> queryForTriple(String sql,
                                           final BeanCreator<X> xCreator,
                                           final BeanCreator<Y> yCreator,
                                           final BeanCreator<Z> zCreator,
                                           final BeanCreator<W> wCreator,
                                           Object... params) {
      try {
         return genericQuery(sql, new QueryCallback<Quadruple<X, Y, Z, W>>() {
            public Quadruple<X, Y, Z, W> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               Z z = zCreator.createBean(rs);
               W w = wCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               if(z instanceof JdbcAware) {
                  ((JdbcAware) z).setJdbcHelper(JdbcHelper.this);
               }
               if(w instanceof JdbcAware) {
                  ((JdbcAware) w).setJdbcHelper(JdbcHelper.this);
               }

               return new Quadruple<X, Y, Z, W>(x, y, z, w);
            }
         }, params);
      } catch (NoResultException e) {
         return null;
      }
   }

   public <X, Y, Z, W, Q> Pentuple<X, Y, Z, W, Q> queryForTriple(String sql,
                                           final BeanCreator<X> xCreator,
                                           final BeanCreator<Y> yCreator,
                                           final BeanCreator<Z> zCreator,
                                           final BeanCreator<W> wCreator,
                                           final BeanCreator<Q> qCreator,
                                           Object... params) {
      try {
         return genericQuery(sql, new QueryCallback<Pentuple<X, Y, Z, W, Q>>() {
            public Pentuple<X, Y, Z, W, Q> process(ResultSet rs) throws SQLException {
               X x = xCreator.createBean(rs);
               Y y = yCreator.createBean(rs);
               Z z = zCreator.createBean(rs);
               W w = wCreator.createBean(rs);
               Q q = qCreator.createBean(rs);
               if(x instanceof JdbcAware) {
                  ((JdbcAware) x).setJdbcHelper(JdbcHelper.this);
               }
               if(y instanceof JdbcAware) {
                  ((JdbcAware) y).setJdbcHelper(JdbcHelper.this);
               }
               if(z instanceof JdbcAware) {
                  ((JdbcAware) z).setJdbcHelper(JdbcHelper.this);
               }
               if(w instanceof JdbcAware) {
                  ((JdbcAware) w).setJdbcHelper(JdbcHelper.this);
               }
               if(q instanceof JdbcAware) {
                  ((JdbcAware) q).setJdbcHelper(JdbcHelper.this);
               }

               return new Pentuple<X, Y, Z, W, Q>(x, y, z, w, q);
            }
         }, params);
      } catch (NoResultException e) {
         return null;
      }
   }

   public int queryForInt(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Integer>() {
         public Integer process(ResultSet rs) throws SQLException {
            return rs.getInt(1);
         }
      }, params);
   }

   public String queryForString(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<String>() {
         public String process(ResultSet rs) throws SQLException {
            return rs.getString(1);
         }
      }, params);
   }

   public long queryForLong(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Long>() {
         public Long process(ResultSet rs) throws SQLException {
            return rs.getLong(1);
         }
      }, params);
   }

   public double queryForDouble(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Double>() {
         public Double process(ResultSet rs) throws SQLException {
            return rs.getDouble(1);
         }
      }, params);
   }

   public float queryForFloat(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Float>() {
         public Float process(ResultSet rs) throws SQLException {
            return rs.getFloat(1);
         }
      }, params);
   }

   public Timestamp queryForTimestamp(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Timestamp>() {
         public Timestamp process(ResultSet rs) throws SQLException {
            return rs.getTimestamp(1);
         }
      }, params);
   }

   public BigDecimal queryForBigDecimal(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<BigDecimal>() {
         public BigDecimal process(ResultSet rs) throws SQLException {
            return rs.getBigDecimal(1);
         }
      }, params);
   }

   public byte[] queryForBytes(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<byte[]>() {
         public byte[] process(ResultSet rs) throws SQLException {
            return rs.getBytes(1);
         }
      }, params);
   }

   public boolean queryForBoolean(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Boolean>() {
         public Boolean process(ResultSet rs) throws SQLException {
            return rs.getBoolean(1);
         }
      }, params);
   }

   public InputStream queryForAsciiStream(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<InputStream>() {
         public InputStream process(ResultSet rs) throws SQLException {
            return rs.getAsciiStream(1);
         }
      }, params);
   }

   public InputStream queryForBinaryStream(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<InputStream>() {
         public InputStream process(ResultSet rs) throws SQLException {
            return rs.getBinaryStream(1);
         }
      }, params);
   }

   public Reader queryForCharacterStream(String sql, Object... params) throws NoResultException {
      return genericQuery(sql, new QueryCallback<Reader>() {
         public Reader process(ResultSet rs) throws SQLException {
            return rs.getCharacterStream(1);
         }
      }, params);
   }

   @SuppressWarnings("unchecked")
   /**
    * Performs the given query
    *
    * @return Returns true if the query is successful and a non-empty resultset was returned
    */
   public boolean query(String sql, final ResultSetHandler handler, Object... params) {
      try {
         genericQuery(sql, new ParameteredQueryCallback(handler) {
            public Object process(ResultSet rs) throws SQLException {
               this.handler.rowNo++;
               this.handler.processRow(rs);
               return null;
            }
         }, params);
         return true;
      } catch (NoResultException e) {
         return false;
      }
   }

   public QueryResult query(String sql, Object... params) {
      return new QueryResult(this, sql, params);
   }

   protected PreparedStatement fillStatement(PreparedStatement stmt, Object[] params) throws SQLException {
      for (int i = 0; i < params.length; i++) {
         if (params[i] != null) {
            stmt.setObject(i + 1, params[i]);
         } else {
            stmt.setNull(i + 1, Types.VARCHAR);
         }
      }

      return stmt;
   }

   public int execute(String sql, Object... params) {
      Connection con = null;
      Statement stmt = null;

      try {
         con = getConnection();

         if (params.length == 0) {
            stmt = con.createStatement();
            return stmt.executeUpdate(sql);
         } else {
            stmt = fillStatement(con.prepareStatement(sql), params);
            return ((PreparedStatement) stmt).executeUpdate();
         }
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   public int executeOnCatalog(String cataLogName, String sql, Object... params) {
      Connection con = null;
      Statement stmt = null;

      try {
         con = getConnection();
         String currentCatalog = con.getCatalog();
         con.setCatalog(cataLogName);

         if (params.length == 0) {
            stmt = con.createStatement();
            int result = stmt.executeUpdate(sql);
            con.setCatalog(currentCatalog);
            return result;
         } else {
            stmt = fillStatement(con.prepareStatement(sql), params);
            int result = ((PreparedStatement) stmt).executeUpdate();
            con.setCatalog(currentCatalog);
            return result;
         }
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   public <T> int execute(String sql, T bean, StatementMapper<T> mapper) {
      Connection con = null;
      PreparedStatement stmt = null;

      try {
         con = getConnection();
         stmt = con.prepareStatement(sql);
         mapper.mapStatement(stmt, bean);
         return stmt.executeUpdate();
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   public <T> int executeOnCatalog(String cataLogName, String sql, T bean, StatementMapper<T> mapper) {
      Connection con = null;
      PreparedStatement stmt = null;

      try {
         con = getConnection();
         String currentCatalog = con.getCatalog();
         con.setCatalog(cataLogName);
         stmt = con.prepareStatement(sql);
         mapper.mapStatement(stmt, bean);
         int result = stmt.executeUpdate();
         con.setCatalog(currentCatalog);
         return result;
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   public void run(String sql, Object... params) {
      Connection con = null;
      Statement stmt = null;

      try {
         con = getConnection();

         if (params.length == 0) {
            stmt = con.createStatement();
            stmt.execute(sql);
         } else {
            stmt = fillStatement(con.prepareStatement(sql), params);
            ((PreparedStatement) stmt).execute();
         }
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   public ExecutableStatement prepareStatement(String sql) {
      return new ExecutableStatement(this, sql);
   }

   public int[] executeBatch(String sql, BatchFeeder feeder) {
      Connection con = null;
      PreparedStatement stmt = null;

      try {
         con = getConnection();

         stmt = con.prepareStatement(sql);
         while (feeder.hasNext()) {
            stmt.clearParameters();
            feeder.feedStatement(stmt);
            stmt.addBatch();
         }
         return stmt.executeBatch();
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   public int[] executeBatch(String sql, SkippingBatchFeeder feeder) {
      Connection con = null;
      PreparedStatement stmt = null;

      try {
         con = getConnection();

         stmt = con.prepareStatement(sql);
         while (feeder.hasNext()) {
            stmt.clearParameters();
            if(feeder.feedStatement(stmt)) {
               stmt.addBatch();
            }
         }
         return stmt.executeBatch();
      } catch (SQLException e) {
         logger.warning("Error executing query:\n" + sql + "\n\nError: " + e.getMessage());
         throw new JdbcException("Error executing query:\n" + sql + "\n\nError: " + e.getMessage(), e);
      } finally {
         JdbcUtil.close(stmt);
         freeConnection(con);
      }
   }

   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      for(Transaction t : transactions.values()) {
         if(t.connection != null && !t.connection.isClosed()) {
            JdbcUtil.close(t.connection);
         }
      }
   }
}
