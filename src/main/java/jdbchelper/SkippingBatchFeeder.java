package jdbchelper;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * User: erdinc
 * Date: 14-Aug-2009
 * Time: 10:34:51
 */
public interface SkippingBatchFeeder {
   public boolean hasNext();
   public boolean feedStatement(PreparedStatement stmt) throws SQLException;
}
