package jdbchelper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ResultSetProcessors are used for creating and returning
 * a bean instance for every row within a result set.
 *
 * @param <T> Type of the bean that will be created
 */
public interface BeanCreator<T> {
   public T createBean(ResultSet rs) throws SQLException;
}
