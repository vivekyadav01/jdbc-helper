package jdbchelper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Author: Erdinc YILMAZEL
 * Date: Dec 25, 2008
 * Time: 1:04:47 AM
 */
public abstract class ResultSetHandler {
   public ResultSetHandler() {

   }

   public ResultSetHandler(int fetchSize) {
      this.fetchSize = fetchSize;
   }

   public ResultSetHandler(int fetchSize, int maxRows) {
      this.fetchSize = fetchSize;
      this.maxRows = maxRows;
   }

   public ResultSetHandler(int fetchSize, int maxRows, int timeOut) {
      this.fetchSize = fetchSize;
      this.maxRows = maxRows;
      this.timeOut = timeOut;
   }

   int fetchSize;
   int maxRows;
   int timeOut;
   int rowNo;

   public int getRowNo() {
      return rowNo;
   }

   /**
    * Should do whatever is needed for the row.
    * The next() method of the resultset is called prior to calling this method
    * so you should never call the next() method within the processRow method
    * unless you know what you are doing.
    * @param rs Fetched resultSet
    * @throws SQLException Thrown in case of a db error
    */
   public abstract void processRow(ResultSet rs) throws SQLException;
}
