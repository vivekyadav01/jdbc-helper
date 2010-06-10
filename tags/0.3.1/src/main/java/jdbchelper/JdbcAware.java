package jdbchelper;

/**
 * If you want the objects that are created by JdbcHelper using a {@link jdbchelper.BeanCreator} to hold a
 * reference to the JdbcHelper object that created them, you can make that type of objects to implement
 * this interface.
 *
 *
 */
public interface JdbcAware {
   public void setJdbcHelper(JdbcHelper jdbc);
}
