package jdbchelper;


/**
 * Author: Erdinc YILMAZEL
 * Date: Mar 12, 2009
 * Time: 2:39:16 PM
 */
public class Tuple<X, Y> {
   final X x;
   final Y y;

   public Tuple(X x, Y y) {
      this.x = x;
      this.y = y;
   }

   public X getFirst() {
      return x;
   }

   public Y getSecond() {
      return y;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Tuple tuple = (Tuple) o;

      return !(x != null ? !x.equals(tuple.x) : tuple.x != null) &&
         !(y != null ? !y.equals(tuple.y) : tuple.y != null);
   }

   @Override
   public int hashCode() {
      int result = x != null ? x.hashCode() : 0;
      result = 31 * result + (y != null ? y.hashCode() : 0);
      return result;
   }
}
