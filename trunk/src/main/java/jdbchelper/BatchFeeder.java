package jdbchelper;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Author: Erdinc YILMAZEL
 * Date: Dec 30, 2008
 * Time: 4:15:43 PM
 */
public interface BatchFeeder {
   public boolean hasNext();
   public void feedStatement(PreparedStatement stmt) throws SQLException;
}
