package jdbchelper;

/**
 * Author: Erdinc YILMAZEL
 * Date: Mar 12, 2009
 * Time: 2:47:05 PM
 */
public class Pentuple<X, Y, Z, W, Q> extends Quadruple<X, Y, Z, W> {
   final Q q;

   public Pentuple(X x, Y y, Z z, W w, Q q) {
      super(x, y, z, w);
      this.q = q;
   }

   public Q getFift() {
      return q;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      Pentuple pentuple = (Pentuple) o;

      return !(q != null ? !q.equals(pentuple.q) : pentuple.q != null);

   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (q != null ? q.hashCode() : 0);
      return result;
   }
}
