package jdbchelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

/**
 * Author: Erdinc YILMAZEL
 * Date: Dec 25, 2008
 * Time: 12:29:19 AM
 */
public class JdbcUtil {

   static Logger logger = LoggerFactory.getLogger(JdbcHelper.class);

   public static void close(Statement stmt, ResultSet rs) {
      close(stmt);
      close(rs);
   }

   /**
	 * Close the given JDBC Connection and ignore any thrown exception.
	 * This is useful for typical finally blocks in manual JDBC code.
	 * @param con the JDBC Connection to close (may be <code>null</code>)
	 */
	public static void close(Connection con) {
		if (con != null) {
			try {
				con.close();
			}
			catch (SQLException ex) {
				logger.warn("Could not close JDBC Connection");
			}
			catch (Throwable ex) {
				// We don't trust the JDBC driver: It might throw RuntimeException or Error.
				logger.warn("Unexpected exception on closing JDBC Connection", ex);
			}
		}
	}

	/**
	 * Close the given JDBC Statement and ignore any thrown exception.
	 * This is useful for typical finally blocks in manual JDBC code.
	 * @param stmt the JDBC Statement to close (may be <code>null</code>)
	 */
	public static void close(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			}
			catch (SQLException ex) {
				logger.warn("Could not close JDBC Statement");
			}
			catch (Throwable ex) {
				// We don't trust the JDBC driver: It might throw RuntimeException or Error.
				logger.warn("Unexpected exception on closing JDBC Statement", ex);
			}
		}
	}

	/**
	 * Close the given JDBC ResultSet and ignore any thrown exception.
	 * This is useful for typical finally blocks in manual JDBC code.
	 * @param rs the JDBC ResultSet to close (may be <code>null</code>)
	 */
	public static void close(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (SQLException ex) {
				logger.warn("Could not close JDBC ResultSet");
			}
			catch (Throwable ex) {
				// We don't trust the JDBC driver: It might throw RuntimeException or Error.
				logger.warn("Unexpected exception on closing JDBC ResultSet", ex);
			}
		}
	}
}
